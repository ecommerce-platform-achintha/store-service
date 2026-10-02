package com.achintha.storeservice.store;

import com.achintha.storeservice.config.OpenApiConfig;
import com.achintha.storeservice.security.Actor;
import com.achintha.storeservice.security.SecurityState;
import com.achintha.storeservice.security.SecurityStateFilter;
import com.achintha.storeservice.store.StoreRequests.CodRequest;
import com.achintha.storeservice.store.StoreRequests.StoreProfileRequest;
import com.achintha.storeservice.store.StoreResponses.MerchantStoreResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The merchant's store profile and COD switch. Owner only (section 3.4): no assistant permission covers the store
 * profile, so assistants are refused. The store is always the one in the JWT {@code storeId}.
 */
@RestController
@RequestMapping("/api/merchant/store")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAuthority('ROLE_MERCHANT')")
@RequiredArgsConstructor
@Tag(name = "Merchant: store")
public class MerchantStoreController {

    private final StoreService storeService;

    @GetMapping
    @Operation(summary = "The caller's store profile (404 until it is first saved)")
    public MerchantStoreResponse get(@AuthenticationPrincipal Jwt jwt) {
        return storeService.getOwn(Actor.from(jwt));
    }

    @PutMapping
    @Operation(summary = "Create (first time) or replace the store profile; the name must be unique. Published "
            + "once the profile is complete and the merchant is approved")
    public MerchantStoreResponse put(@AuthenticationPrincipal Jwt jwt,
                                     @RequestAttribute(name = SecurityStateFilter.STATE_ATTRIBUTE, required = false)
                                     SecurityState state,
                                     @Valid @RequestBody StoreProfileRequest request) {
        return storeService.upsertProfile(Actor.from(jwt), request, state);
    }

    @PutMapping("/cod")
    @Operation(summary = "Turn cash on delivery on or off for the store")
    public MerchantStoreResponse cod(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CodRequest request) {
        return storeService.setCod(Actor.from(jwt), request.enabled());
    }
}
