package com.achintha.storeservice.review;

import com.achintha.storeservice.common.PageResponse;
import com.achintha.storeservice.common.Pagination;
import com.achintha.storeservice.config.OpenApiConfig;
import com.achintha.storeservice.review.ReviewDtos.ManagedReviewResponse;
import com.achintha.storeservice.review.ReviewDtos.ReplyRequest;
import com.achintha.storeservice.security.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The store's reviews and replies: the merchant, or an assistant with {@code REVIEW_REPLY}. */
@RestController
@RequestMapping("/api/merchant/reviews")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAuthority('PERM_REVIEW_REPLY')")
@RequiredArgsConstructor
@Tag(name = "Merchant: store reviews")
public class MerchantReviewController {

    static final Map<String, String> SORTABLE = Map.of("createdAt", "createdAt", "rating", "rating");

    private final ReviewService reviewService;

    @GetMapping
    @Operation(summary = "Reviews of the caller's store, hidden ones included")
    public PageResponse<ManagedReviewResponse> list(@AuthenticationPrincipal Jwt jwt,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size,
                                                    @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return reviewService.merchantReviews(Actor.from(jwt),
                Pagination.of(page, size, sort, SORTABLE, Pagination.MAX_SIZE));
    }

    @PostMapping("/{publicId}/reply")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Reply to a review of the store (one reply per review)")
    public ManagedReviewResponse reply(@AuthenticationPrincipal Jwt jwt, @PathVariable String publicId,
                                       @Valid @RequestBody ReplyRequest request) {
        return reviewService.reply(Actor.from(jwt), publicId, request.text());
    }
}
