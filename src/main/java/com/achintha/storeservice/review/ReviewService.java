package com.achintha.storeservice.review;

import com.achintha.storeservice.audit.AuditService;
import com.achintha.storeservice.common.PageResponse;
import com.achintha.storeservice.common.PublicIdGenerator;
import com.achintha.storeservice.common.TextSanitizer;
import com.achintha.storeservice.exception.ApiException;
import com.achintha.storeservice.exception.ErrorCode;
import com.achintha.storeservice.exception.NotFoundException;
import com.achintha.storeservice.ports.ImageStoragePort;
import com.achintha.storeservice.review.ReviewDtos.CreateReviewRequest;
import com.achintha.storeservice.review.ReviewDtos.ManagedReviewResponse;
import com.achintha.storeservice.review.ReviewDtos.ReplyResponse;
import com.achintha.storeservice.review.ReviewDtos.ReviewResponse;
import com.achintha.storeservice.security.Actor;
import com.achintha.storeservice.security.SecurityState;
import com.achintha.storeservice.security.UserStatus;
import com.achintha.storeservice.store.Store;
import com.achintha.storeservice.store.StoreQueryService;
import com.achintha.storeservice.store.StoreRepository;
import com.achintha.storeservice.store.StoreService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Store reviews (section 4): a customer may review a store once per completed order (eligibility from
 * {@code OrderCompleted}); the store may reply once; admins hide and unhide with a reason. The store's rating
 * aggregates count visible reviews only.
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final StoreReviewRepository reviewRepository;
    private final ReviewReplyRepository replyRepository;
    private final ReviewEligibilityRepository eligibilityRepository;
    private final StoreRepository storeRepository;
    private final StoreQueryService storeQueryService;
    private final ImageStoragePort imageStorage;
    private final PublicIdGenerator publicIds;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    // ------------------------------------------------------------------------------------------ eligibility

    /** Idempotent: a second {@code OrderCompleted} for the same order changes nothing. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordEligibility(UUID orderId, String orderPublicId, UUID storeId, UUID customerId,
                                  Instant completedAt) {
        jdbcTemplate.update("""
                insert into review_eligibility (order_id, order_public_id, store_id, customer_id, completed_at)
                values (?, ?, ?, ?, ?) on conflict do nothing
                """, orderId, orderPublicId, storeId, customerId, Timestamp.from(completedAt));
    }

    // --------------------------------------------------------------------------------------------- customer

    @Transactional
    public ReviewResponse create(Actor actor, String storePublicId, CreateReviewRequest request,
                                 SecurityState customerState) {
        if (customerState != null && customerState.status() == UserStatus.BANNED) {
            throw ApiException.forbidden(ErrorCode.ACCOUNT_BANNED, "Banned accounts cannot write reviews");
        }
        Store store = storeQueryService.requireAny(storePublicId);
        ReviewEligibility eligibility = eligibilityRepository
                .findByOrderPublicIdAndCustomerIdAndStoreId(request.orderPublicId(), actor.id(), store.getId())
                .orElseThrow(() -> new NotFoundException("No completed order of yours with this store has this id"));
        if (reviewRepository.existsByOrderId(eligibility.getOrderId())) {
            throw alreadyReviewed();
        }
        List<String> keys = request.attachmentKeys() == null ? List.of() : List.copyOf(request.attachmentKeys());
        for (String key : keys) {
            if (!imageStorage.exists(key)) {
                throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "attachmentKeys must refer to uploaded images");
            }
        }
        Instant now = clock.instant();
        StoreReview review = new StoreReview();
        review.setId(UUID.randomUUID());
        review.setPublicId(publicIds.generateUnique(PublicIdGenerator.REVIEW_PREFIX,
                reviewRepository::existsByPublicId));
        review.setStoreId(store.getId());
        review.setOrderId(eligibility.getOrderId());
        review.setOrderPublicId(eligibility.getOrderPublicId());
        review.setCustomerId(actor.id());
        review.setRating(request.rating().shortValue());
        review.setBody(TextSanitizer.clean(request.text()));
        review.setAttachmentKeys(keys);
        review.setCreatedAt(now);
        review.setUpdatedAt(now);
        try {
            review = reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            throw alreadyReviewed();
        }
        storeRepository.refreshRating(store.getId());
        return toPublic(review, null);
    }

    // ----------------------------------------------------------------------------------------------- public

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> publicReviews(String storePublicId, Pageable pageable) {
        Store store = storeQueryService.requireVisible(storePublicId);
        Page<StoreReview> page = reviewRepository.findAllByStoreIdAndHiddenFalse(store.getId(), pageable);
        Map<UUID, ReviewReply> replies = repliesOf(page);
        return PageResponse.from(page, review -> toPublic(review, replies.get(review.getId())));
    }

    // ---------------------------------------------------------------------------------------------- merchant

    @Transactional(readOnly = true)
    public PageResponse<ManagedReviewResponse> merchantReviews(Actor actor, Pageable pageable) {
        Page<StoreReview> page = reviewRepository.findAllByStoreId(StoreService.storeIdOf(actor), pageable);
        Map<UUID, ReviewReply> replies = repliesOf(page);
        return PageResponse.from(page, review -> toManaged(review, replies.get(review.getId())));
    }

    @Transactional
    public ManagedReviewResponse reply(Actor actor, String reviewPublicId, String rawText) {
        StoreReview review = reviewRepository.findByPublicIdAndStoreId(reviewPublicId, StoreService.storeIdOf(actor))
                .orElseThrow(() -> new NotFoundException("Review not found"));
        if (replyRepository.existsByReviewId(review.getId())) {
            throw replyExists();
        }
        String text = TextSanitizer.clean(rawText);
        if (text == null) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "text must not be blank");
        }
        ReviewReply reply = new ReviewReply();
        reply.setId(UUID.randomUUID());
        reply.setReviewId(review.getId());
        reply.setStoreId(review.getStoreId());
        reply.setBody(text);
        reply.setAuthorUserId(actor.id());
        reply.setAuthorPublicId(actor.publicId());
        reply.setAuthorRole(actor.role());
        reply.setCreatedAt(clock.instant());
        try {
            reply = replyRepository.saveAndFlush(reply);
        } catch (DataIntegrityViolationException e) {
            throw replyExists();
        }
        auditService.record(actor, "REVIEW_REPLIED", AuditService.TARGET_REVIEW, review.getPublicId(), null,
                Map.of("reply", text), null);
        return toManaged(review, reply);
    }

    // ------------------------------------------------------------------------------------------------- admin

    @Transactional(readOnly = true)
    public PageResponse<ManagedReviewResponse> adminReviews(String storePublicId, Pageable pageable) {
        Store store = storeQueryService.requireAny(storePublicId);
        Page<StoreReview> page = reviewRepository.findAllByStoreId(store.getId(), pageable);
        Map<UUID, ReviewReply> replies = repliesOf(page);
        return PageResponse.from(page, review -> toManaged(review, replies.get(review.getId())));
    }

    @Transactional
    public ManagedReviewResponse setHidden(Actor actor, String reviewPublicId, boolean hidden, String rawReason) {
        StoreReview review = reviewRepository.findByPublicId(reviewPublicId)
                .orElseThrow(() -> new NotFoundException("Review not found"));
        String reason = TextSanitizer.cleanLine(rawReason);
        if (reason == null) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "reason must not be blank");
        }
        boolean before = review.isHidden();
        Instant now = clock.instant();
        review.setHidden(hidden);
        review.setHiddenReason(hidden ? reason : null);
        review.setHiddenBy(hidden ? actor.publicId() : null);
        review.setHiddenAt(hidden ? now : null);
        review.setUpdatedAt(now);
        reviewRepository.saveAndFlush(review);
        if (before != hidden) {
            storeRepository.refreshRating(review.getStoreId());
        }
        auditService.record(actor, hidden ? "REVIEW_HIDDEN" : "REVIEW_UNHIDDEN", AuditService.TARGET_REVIEW,
                review.getPublicId(), Map.of("hidden", before), Map.of("hidden", hidden), reason);
        ReviewReply reply = replyRepository.findAllByReviewIdIn(List.of(review.getId())).stream().findFirst()
                .orElse(null);
        return toManaged(review, reply);
    }

    // ------------------------------------------------------------------------------------------------- helpers

    private Map<UUID, ReviewReply> repliesOf(Page<StoreReview> page) {
        List<UUID> ids = page.getContent().stream().map(StoreReview::getId).toList();
        return ids.isEmpty() ? Map.of() : replyRepository.findAllByReviewIdIn(ids).stream()
                .collect(Collectors.toMap(ReviewReply::getReviewId, Function.identity()));
    }

    private ReviewResponse toPublic(StoreReview review, ReviewReply reply) {
        return new ReviewResponse(review.getPublicId(), review.getRating(), review.getBody(), urls(review),
                review.getCreatedAt(), reply == null ? null : new ReplyResponse(reply.getBody(), reply.getCreatedAt()));
    }

    private ManagedReviewResponse toManaged(StoreReview review, ReviewReply reply) {
        return new ManagedReviewResponse(review.getPublicId(), review.getOrderPublicId(), review.getRating(),
                review.getBody(), urls(review), review.isHidden(), review.getHiddenReason(), review.getHiddenAt(),
                review.getCreatedAt(),
                reply == null ? null : new ReplyResponse(reply.getBody(), reply.getCreatedAt()));
    }

    private List<String> urls(StoreReview review) {
        return review.getAttachmentKeys().stream().map(imageStorage::urlFor).toList();
    }

    private static ApiException alreadyReviewed() {
        return ApiException.conflict(ErrorCode.REVIEW_ALREADY_EXISTS, "This order has already been reviewed");
    }

    private static ApiException replyExists() {
        return ApiException.conflict(ErrorCode.REPLY_ALREADY_EXISTS, "This review already has a reply");
    }
}
