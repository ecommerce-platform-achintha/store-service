package com.achintha.storeservice.bank;

import com.achintha.storeservice.bank.BankDtos.BankResponse;
import com.achintha.storeservice.bank.BankDtos.CreateBankRequest;
import com.achintha.storeservice.bank.BankDtos.UpdateBankRequest;
import com.achintha.storeservice.common.PageResponse;
import com.achintha.storeservice.common.Pagination;
import com.achintha.storeservice.config.OpenApiConfig;
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

/**
 * Bank master data under {@code /api/admin/banks} (the gateway route): admins may read, only the super admin may
 * change it.
 */
@RestController
@RequestMapping("/api/admin/banks")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin: banks")
public class BankAdminController {

    private static final Map<String, String> SORTABLE = Map.of("code", "code", "name", "name");

    private final BankService bankService;

    @GetMapping
    @Operation(summary = "List banks (optionally only active or inactive ones)")
    public PageResponse<BankResponse> list(@RequestParam(required = false) Boolean active,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size,
                                           @RequestParam(defaultValue = "name,asc") String sort) {
        return bankService.list(active, Pagination.of(page, size, sort, SORTABLE, Pagination.MAX_ADMIN_SIZE));
    }

    @GetMapping("/{code}")
    public BankResponse get(@PathVariable String code) {
        return bankService.get(code);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Add a bank (super admin)")
    public BankResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateBankRequest request) {
        return bankService.create(Actor.from(jwt), request);
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Rename or (de)activate a bank (super admin)")
    public BankResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable String code,
                               @Valid @RequestBody UpdateBankRequest request) {
        return bankService.update(Actor.from(jwt), code, request);
    }

    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Delete an unused bank (super admin); 409 BANK_IN_USE otherwise")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String code) {
        bankService.delete(Actor.from(jwt), code);
    }
}
