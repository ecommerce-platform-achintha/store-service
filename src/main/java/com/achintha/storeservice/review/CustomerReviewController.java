package com.achintha.storeservice.review;

import com.achintha.storeservice.config.OpenApiConfig;
import com.achintha.storeservice.review.ReviewDtos.CreateReviewRequest;
import com.achintha.storeservice.review.ReviewDtos.ReviewResponse;
import com.achintha.storeservice.security.Actor;
import com.achintha.storeservice.security.SecurityState;
import com.achintha.storeservice.security.SecurityStateFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
@RequiredArgsConstructor
@Tag(name = "Customer: store reviews")
public class CustomerReviewController {

    private final ReviewService reviewService;

    @PostMapping("/api/customer/stores/{publicId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Review a store for one of your completed orders (one review per order, rating 1-5)")
    public ReviewResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable String publicId,
                                 @RequestAttribute(name = SecurityStateFilter.STATE_ATTRIBUTE, required = false)
                                 SecurityState state,
                                 @Valid @RequestBody CreateReviewRequest request) {
        return reviewService.create(Actor.from(jwt), publicId, request, state);
    }
}
