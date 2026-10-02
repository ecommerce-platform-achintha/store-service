package com.achintha.storeservice.courier;

import com.achintha.storeservice.common.PageResponse;
import com.achintha.storeservice.common.Pagination;
import com.achintha.storeservice.config.OpenApiConfig;
import com.achintha.storeservice.courier.CourierDtos.CourierRequest;
import com.achintha.storeservice.courier.CourierDtos.CourierResponse;
import com.achintha.storeservice.courier.CourierDtos.UpdateCourierRequest;
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

/** Courier master data under {@code /api/admin/couriers}: admins may read, only the super admin may change it. */
@RestController
@RequestMapping("/api/admin/couriers")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin: couriers")
public class CourierAdminController {

    private static final Map<String, String> SORTABLE = Map.of("code", "code", "name", "name");

    private final CourierService courierService;

    @GetMapping
    @Operation(summary = "List couriers (optionally only active or inactive ones)")
    public PageResponse<CourierResponse> list(@RequestParam(required = false) Boolean active,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "50") int size,
                                              @RequestParam(defaultValue = "name,asc") String sort) {
        return courierService.list(active, Pagination.of(page, size, sort, SORTABLE, Pagination.MAX_ADMIN_SIZE));
    }

    @GetMapping("/{code}")
    public CourierResponse get(@PathVariable String code) {
        return courierService.get(code);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Add a courier: https tracking URL template with {trackingNumber}, tracking number regex, "
            + "COD support (super admin)")
    public CourierResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CourierRequest request) {
        return courierService.create(Actor.from(jwt), request);
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Change or (de)activate a courier (super admin)")
    public CourierResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable String code,
                                  @Valid @RequestBody UpdateCourierRequest request) {
        return courierService.update(Actor.from(jwt), code, request);
    }

    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Delete an unused courier (super admin); 409 COURIER_IN_USE otherwise")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String code) {
        courierService.delete(Actor.from(jwt), code);
    }
}
