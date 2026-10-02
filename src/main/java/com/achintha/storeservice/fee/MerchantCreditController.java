package com.achintha.storeservice.fee;

import com.achintha.storeservice.common.Pagination;
import com.achintha.storeservice.config.OpenApiConfig;
import com.achintha.storeservice.fee.FeeDtos.CreditRequest;
import com.achintha.storeservice.fee.FeeDtos.CreditResponse;
import com.achintha.storeservice.fee.FeeDtos.LedgerResponse;
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

/** Fee ledger and credits (D11, recorded only). Super admin only; merchants are addressed by store public id. */
@RestController
@RequestMapping("/api/super-admin/merchants/{storePublicId}")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Super admin: fees and credits")
public class MerchantCreditController {

    private static final Map<String, String> SORTABLE = Map.of("createdAt", "createdAt");

    private final FeeLedgerService feeLedgerService;

    @PostMapping("/credits")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Grant a credit to a merchant (reason required, audited)")
    public CreditResponse credit(@AuthenticationPrincipal Jwt jwt, @PathVariable String storePublicId,
                                 @Valid @RequestBody CreditRequest request) {
        return feeLedgerService.grantCredit(Actor.from(jwt), storePublicId, request.amount(), request.reason());
    }

    @GetMapping("/ledger")
    @Operation(summary = "The merchant's fee ledger and balance (informational, never enforced)")
    public LedgerResponse ledger(@PathVariable String storePublicId,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size,
                                 @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return feeLedgerService.ledger(storePublicId,
                Pagination.of(page, size, sort, SORTABLE, Pagination.MAX_ADMIN_SIZE));
    }
}
