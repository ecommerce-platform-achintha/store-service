package com.achintha.storeservice.courier;

import com.achintha.storeservice.config.OpenApiConfig;
import com.achintha.storeservice.courier.CourierDtos.CourierResponse;
import com.achintha.storeservice.courier.CourierDtos.StoreCourierResponse;
import com.achintha.storeservice.courier.CourierDtos.StoreCouriersRequest;
import com.achintha.storeservice.security.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Couriers the store ships with. Owner only. Bounded lists (at most 20 couriers), so not paged. */
@RestController
@RequestMapping("/api/merchant/owner/couriers")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAuthority('ROLE_MERCHANT')")
@RequiredArgsConstructor
@Tag(name = "Merchant owner: couriers")
public class MerchantCourierController {

    private final CourierService courierService;

    @GetMapping
    @Operation(summary = "Couriers enabled for the store")
    public List<StoreCourierResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return courierService.ownCouriers(Actor.from(jwt));
    }

    @GetMapping("/available")
    @Operation(summary = "Active couriers of the master list that can be enabled")
    public List<CourierResponse> available() {
        return courierService.activeCouriers();
    }

    @PutMapping
    @Operation(summary = "Replace the enabled couriers with this list of master-list codes")
    public List<StoreCourierResponse> replace(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody StoreCouriersRequest request) {
        return courierService.replaceOwnCouriers(Actor.from(jwt), request.courierCodes());
    }
}
