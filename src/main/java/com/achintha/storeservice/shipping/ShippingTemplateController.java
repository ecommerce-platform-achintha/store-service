package com.achintha.storeservice.shipping;

import com.achintha.storeservice.common.PageResponse;
import com.achintha.storeservice.common.Pagination;
import com.achintha.storeservice.config.OpenApiConfig;
import com.achintha.storeservice.security.Actor;
import com.achintha.storeservice.shipping.ShippingDtos.ShippingTemplateRequest;
import com.achintha.storeservice.shipping.ShippingDtos.ShippingTemplateResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Shipping templates (flat, per district, weight bands; estimates only). Owner only. */
@RestController
@RequestMapping("/api/merchant/owner/shipping-templates")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAuthority('ROLE_MERCHANT')")
@RequiredArgsConstructor
@Tag(name = "Merchant owner: shipping templates")
public class ShippingTemplateController {

    private static final Map<String, String> SORTABLE = Map.of("createdAt", "createdAt", "courierCode",
            "courierCode", "name", "name");

    private final ShippingTemplateService service;

    @GetMapping
    public PageResponse<ShippingTemplateResponse> list(@AuthenticationPrincipal Jwt jwt,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size,
                                                       @RequestParam(defaultValue = "courierCode,asc") String sort) {
        return service.list(Actor.from(jwt), Pagination.of(page, size, sort, SORTABLE, Pagination.MAX_SIZE));
    }

    @GetMapping("/{publicId}")
    public ShippingTemplateResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String publicId) {
        return service.get(Actor.from(jwt), publicId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a template for an enabled courier (one per courier)")
    public ShippingTemplateResponse create(@AuthenticationPrincipal Jwt jwt,
                                           @Valid @RequestBody ShippingTemplateRequest request) {
        return service.create(Actor.from(jwt), request);
    }

    @PutMapping("/{publicId}")
    public ShippingTemplateResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable String publicId,
                                           @Valid @RequestBody ShippingTemplateRequest request) {
        return service.update(Actor.from(jwt), publicId, request);
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String publicId) {
        service.delete(Actor.from(jwt), publicId);
    }
}
