package com.achintha.storeservice.bank;

import com.achintha.storeservice.bank.BankAccountDtos.BankAccountResponse;
import com.achintha.storeservice.bank.BankAccountDtos.CreateBankAccountRequest;
import com.achintha.storeservice.bank.BankAccountDtos.UpdateBankAccountRequest;
import com.achintha.storeservice.bank.BankDtos.BankResponse;
import com.achintha.storeservice.common.PageResponse;
import com.achintha.storeservice.common.Pagination;
import com.achintha.storeservice.config.OpenApiConfig;
import com.achintha.storeservice.security.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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

/** The store's bank accounts. Owner only; account numbers are always masked here. */
@RestController
@RequestMapping("/api/merchant/owner/bank-accounts")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAuthority('ROLE_MERCHANT')")
@RequiredArgsConstructor
@Tag(name = "Merchant owner: bank accounts")
public class BankAccountController {

    private static final Map<String, String> SORTABLE = Map.of("createdAt", "createdAt", "bankCode", "bankCode");

    private final BankAccountService bankAccountService;
    private final BankService bankService;

    @GetMapping("/banks")
    @Operation(summary = "Banks an account can be opened with (active master data)")
    public List<BankResponse> banks() {
        return bankService.activeBanks();
    }

    @GetMapping
    @Operation(summary = "The store's bank accounts (masked)")
    public PageResponse<BankAccountResponse> list(@AuthenticationPrincipal Jwt jwt,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(defaultValue = "createdAt,asc") String sort) {
        return bankAccountService.list(Actor.from(jwt),
                Pagination.of(page, size, sort, SORTABLE, Pagination.MAX_SIZE));
    }

    @GetMapping("/{publicId}")
    public BankAccountResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String publicId) {
        return bankAccountService.get(Actor.from(jwt), publicId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a bank account (bank from the master list; number stored encrypted)")
    public BankAccountResponse create(@AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody CreateBankAccountRequest request) {
        return bankAccountService.create(Actor.from(jwt), request);
    }

    @PutMapping("/{publicId}")
    @Operation(summary = "Change a bank account (omit accountNumber to keep it)")
    public BankAccountResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable String publicId,
                                      @Valid @RequestBody UpdateBankAccountRequest request) {
        return bankAccountService.update(Actor.from(jwt), publicId, request);
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a bank account")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String publicId) {
        bankAccountService.delete(Actor.from(jwt), publicId);
    }
}
