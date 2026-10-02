package com.achintha.storeservice.score;

import com.achintha.storeservice.config.OpenApiConfig;
import com.achintha.storeservice.score.MetricsDtos.MetricsResponse;
import com.achintha.storeservice.security.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The store's score and metrics. Owner only. */
@RestController
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAuthority('ROLE_MERCHANT')")
@RequiredArgsConstructor
@Tag(name = "Merchant owner: metrics")
public class MerchantMetricsController {

    private final MetricsQueryService metricsQueryService;

    @GetMapping("/api/merchant/owner/metrics")
    @Operation(summary = "Score, acceptance rate, average response time, cancellation rate, late shipments, "
            + "completed orders and revenue")
    public MetricsResponse metrics(@AuthenticationPrincipal Jwt jwt) {
        return metricsQueryService.ownMetrics(Actor.from(jwt));
    }
}
