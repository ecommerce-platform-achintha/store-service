package com.achintha.storeservice.store;

import com.achintha.storeservice.common.PageResponse;
import com.achintha.storeservice.common.Pagination;
import com.achintha.storeservice.common.ReasonRequest;
import com.achintha.storeservice.config.OpenApiConfig;
import com.achintha.storeservice.review.ReviewDtos.ManagedReviewResponse;
import com.achintha.storeservice.review.ReviewService;
import com.achintha.storeservice.score.MetricsDtos.AtRiskMerchantResponse;
import com.achintha.storeservice.score.MetricsDtos.MetricsResponse;
import com.achintha.storeservice.score.MetricsDtos.ScoreEventResponse;
import com.achintha.storeservice.score.MetricsQueryService;
import com.achintha.storeservice.security.Actor;
import com.achintha.storeservice.store.StoreResponses.AdminStoreResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin views of stores, merchant scores and review moderation (ROLE_ADMIN or ROLE_SUPER_ADMIN). */
@RestController
@RequestMapping("/api/admin")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin: stores and merchant scores")
public class AdminStoreController {

    private static final Map<String, String> STORE_SORTABLE = Map.of("name", "name", "createdAt", "createdAt",
            "rating", "ratingAvg");
    private static final Map<String, String> AT_RISK_SORTABLE = Map.of("score", "score", "atRiskSince",
            "atRiskSince");
    private static final Map<String, String> SCORE_EVENT_SORTABLE = Map.of("createdAt", "createdAt");
    private static final Map<String, String> REVIEW_SORTABLE = Map.of("createdAt", "createdAt", "rating", "rating");

    private final StoreQueryService storeQueryService;
    private final MetricsQueryService metricsQueryService;
    private final ReviewService reviewService;

    @GetMapping("/stores")
    @Operation(summary = "All stores (q on name), with owner status, visibility and score; max page size 100")
    public PageResponse<AdminStoreResponse> stores(@RequestParam(required = false) String q,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @RequestParam(defaultValue = "name,asc") String sort) {
        return storeQueryService.searchAdmin(q,
                Pagination.of(page, size, sort, STORE_SORTABLE, Pagination.MAX_ADMIN_SIZE));
    }

    @GetMapping("/stores/{publicId}")
    public AdminStoreResponse store(@PathVariable String publicId) {
        return storeQueryService.getAdmin(publicId);
    }

    @GetMapping("/stores/{publicId}/score")
    @Operation(summary = "Score details: score and all store metrics")
    public MetricsResponse score(@PathVariable String publicId) {
        return metricsQueryService.metricsOf(storeQueryService.requireAny(publicId).getId());
    }

    @GetMapping("/stores/{publicId}/score-events")
    @Operation(summary = "Score history of a store (every applied delta)")
    public PageResponse<ScoreEventResponse> scoreEvents(@PathVariable String publicId,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size,
                                                        @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return metricsQueryService.scoreEvents(storeQueryService.requireAny(publicId).getId(),
                Pagination.of(page, size, sort, SCORE_EVENT_SORTABLE, Pagination.MAX_ADMIN_SIZE));
    }

    @GetMapping("/stores/{publicId}/reviews")
    @Operation(summary = "All reviews of a store, hidden ones included")
    public PageResponse<ManagedReviewResponse> reviews(@PathVariable String publicId,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size,
                                                       @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return reviewService.adminReviews(publicId,
                Pagination.of(page, size, sort, REVIEW_SORTABLE, Pagination.MAX_ADMIN_SIZE));
    }

    @PostMapping("/stores/reviews/{reviewPublicId}/hide")
    @Operation(summary = "Hide a store review (reason required; removed from the rating)")
    public ManagedReviewResponse hide(@AuthenticationPrincipal Jwt jwt, @PathVariable String reviewPublicId,
                                      @Valid @RequestBody ReasonRequest request) {
        return reviewService.setHidden(Actor.from(jwt), reviewPublicId, true, request.reason());
    }

    @PostMapping("/stores/reviews/{reviewPublicId}/unhide")
    @Operation(summary = "Show a hidden store review again (reason required)")
    public ManagedReviewResponse unhide(@AuthenticationPrincipal Jwt jwt, @PathVariable String reviewPublicId,
                                        @Valid @RequestBody ReasonRequest request) {
        return reviewService.setHidden(Actor.from(jwt), reviewPublicId, false, request.reason());
    }

    @GetMapping("/merchants/at-risk")
    @Operation(summary = "Merchants whose score is below score.merchant.at-risk-threshold (alert only, D2)")
    public PageResponse<AtRiskMerchantResponse> atRisk(@RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size,
                                                       @RequestParam(defaultValue = "score,asc") String sort) {
        return metricsQueryService.atRisk(
                Pagination.of(page, size, sort, AT_RISK_SORTABLE, Pagination.MAX_ADMIN_SIZE));
    }
}
