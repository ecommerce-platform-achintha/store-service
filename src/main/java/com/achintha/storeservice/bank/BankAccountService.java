package com.achintha.storeservice.bank;

import com.achintha.storeservice.audit.AuditService;
import com.achintha.storeservice.bank.BankAccountDtos.BankAccountResponse;
import com.achintha.storeservice.bank.BankAccountDtos.CreateBankAccountRequest;
import com.achintha.storeservice.bank.BankAccountDtos.InternalBankAccount;
import com.achintha.storeservice.bank.BankAccountDtos.UpdateBankAccountRequest;
import com.achintha.storeservice.common.PageResponse;
import com.achintha.storeservice.common.PublicIdGenerator;
import com.achintha.storeservice.common.TextSanitizer;
import com.achintha.storeservice.common.Validation;
import com.achintha.storeservice.crypto.AccountNumberCipher;
import com.achintha.storeservice.exception.ApiException;
import com.achintha.storeservice.exception.ErrorCode;
import com.achintha.storeservice.exception.NotFoundException;
import com.achintha.storeservice.security.Actor;
import com.achintha.storeservice.store.Store;
import com.achintha.storeservice.store.StoreService;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Store bank accounts (owner only). Every query is scoped by the JWT {@code storeId}; every change writes an audit
 * row with the account number masked. Plain numbers leave this service only through the internal endpoint.
 */
@Service
public class BankAccountService {

    private final StoreBankAccountRepository accountRepository;
    private final BankRepository bankRepository;
    private final BankService bankService;
    private final StoreService storeService;
    private final AccountNumberCipher cipher;
    private final PublicIdGenerator publicIds;
    private final AuditService auditService;
    private final Clock clock;
    private final int maxAccounts;

    public BankAccountService(StoreBankAccountRepository accountRepository, BankRepository bankRepository,
                              BankService bankService, StoreService storeService, AccountNumberCipher cipher,
                              PublicIdGenerator publicIds, AuditService auditService, Clock clock,
                              @Value("${app.stores.max-bank-accounts:10}") int maxAccounts) {
        this.accountRepository = accountRepository;
        this.bankRepository = bankRepository;
        this.bankService = bankService;
        this.storeService = storeService;
        this.cipher = cipher;
        this.publicIds = publicIds;
        this.auditService = auditService;
        this.clock = clock;
        this.maxAccounts = maxAccounts;
    }

    @Transactional(readOnly = true)
    public PageResponse<BankAccountResponse> list(Actor actor, Pageable pageable) {
        UUID storeId = StoreService.storeIdOf(actor);
        Map<String, String> bankNames = bankNames();
        return PageResponse.from(accountRepository.findAllByStoreIdAndDeletedAtIsNull(storeId, pageable),
                account -> toResponse(account, bankNames));
    }

    @Transactional(readOnly = true)
    public BankAccountResponse get(Actor actor, String publicId) {
        return toResponse(require(actor, publicId), bankNames());
    }

    @Transactional
    public BankAccountResponse create(Actor actor, CreateBankAccountRequest request) {
        Store store = storeService.requireOwnStore(actor);
        if (accountRepository.countByStoreIdAndDeletedAtIsNull(store.getId()) >= maxAccounts) {
            throw ApiException.conflict(ErrorCode.BANK_ACCOUNT_LIMIT_REACHED,
                    "A store can have at most " + maxAccounts + " bank accounts");
        }
        bankService.requireActive(request.bankCode());
        String number = normalizeNumber(request.accountNumber());
        Instant now = clock.instant();
        StoreBankAccount account = new StoreBankAccount();
        account.setId(UUID.randomUUID());
        account.setPublicId(publicIds.generateUnique(PublicIdGenerator.BANK_ACCOUNT_PREFIX,
                accountRepository::existsByPublicId));
        account.setStoreId(store.getId());
        account.setBankCode(request.bankCode());
        account.setBranch(requireText(request.branch(), "branch"));
        account.setAccountName(requireText(request.accountName(), "accountName"));
        setNumber(account, number);
        account.setActive(request.active() == null || request.active());
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        account = accountRepository.save(account);
        auditService.record(actor, "BANK_ACCOUNT_CREATED", AuditService.TARGET_BANK_ACCOUNT, account.getPublicId(),
                null, snapshot(account), null);
        return toResponse(account, bankNames());
    }

    @Transactional
    public BankAccountResponse update(Actor actor, String publicId, UpdateBankAccountRequest request) {
        StoreBankAccount account = require(actor, publicId);
        Map<String, Object> before = snapshot(account);
        if (!account.getBankCode().equals(request.bankCode())) {
            bankService.requireActive(request.bankCode());
        }
        account.setBankCode(request.bankCode());
        account.setBranch(requireText(request.branch(), "branch"));
        account.setAccountName(requireText(request.accountName(), "accountName"));
        if (request.accountNumber() != null && !request.accountNumber().isBlank()) {
            setNumber(account, normalizeNumber(request.accountNumber()));
        }
        account.setActive(request.active());
        account.setUpdatedAt(clock.instant());
        account = accountRepository.saveAndFlush(account);
        auditService.record(actor, "BANK_ACCOUNT_UPDATED", AuditService.TARGET_BANK_ACCOUNT, account.getPublicId(),
                before, snapshot(account), null);
        return toResponse(account, bankNames());
    }

    @Transactional
    public void delete(Actor actor, String publicId) {
        StoreBankAccount account = require(actor, publicId);
        Map<String, Object> before = snapshot(account);
        Instant now = clock.instant();
        account.setDeletedAt(now);
        account.setActive(false);
        account.setUpdatedAt(now);
        auditService.record(actor, "BANK_ACCOUNT_DELETED", AuditService.TARGET_BANK_ACCOUNT, account.getPublicId(),
                before, null, null);
    }

    /** Active accounts with full numbers, for order-service (payment display to the order's customer). */
    @Transactional(readOnly = true)
    public List<InternalBankAccount> internalActiveAccounts(UUID storeId) {
        Map<String, String> bankNames = bankNames();
        return accountRepository.findAllByStoreIdAndActiveTrueAndDeletedAtIsNullOrderByCreatedAtAsc(storeId).stream()
                .map(a -> new InternalBankAccount(a.getId(), a.getPublicId(), a.getBankCode(),
                        bankNames.get(a.getBankCode()), a.getBranch(), a.getAccountName(),
                        cipher.decrypt(a.getAccountNumberEncrypted()), AccountNumberCipher.mask(a.getAccountLast4())))
                .toList();
    }

    // ------------------------------------------------------------------------------------------------- helpers

    private StoreBankAccount require(Actor actor, String publicId) {
        return accountRepository.findByPublicIdAndStoreIdAndDeletedAtIsNull(publicId, StoreService.storeIdOf(actor))
                .orElseThrow(() -> new NotFoundException("Bank account not found"));
    }

    private void setNumber(StoreBankAccount account, String number) {
        account.setAccountNumberEncrypted(cipher.encrypt(number));
        account.setAccountLast4(AccountNumberCipher.lastFour(number));
    }

    static String normalizeNumber(String raw) {
        String number = raw == null ? "" : raw.replaceAll("[\\s-]", "");
        if (!number.matches(Validation.ACCOUNT_NUMBER_REGEX)) {
            // Never echo the value back
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "accountNumber must be 6 to 20 digits");
        }
        return number;
    }

    private static String requireText(String raw, String field) {
        String value = TextSanitizer.cleanLine(raw);
        if (value == null) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, field + " must not be blank");
        }
        return value;
    }

    private Map<String, String> bankNames() {
        return bankRepository.findAll().stream().collect(Collectors.toMap(Bank::getCode, Bank::getName));
    }

    private static BankAccountResponse toResponse(StoreBankAccount account, Map<String, String> bankNames) {
        return new BankAccountResponse(account.getPublicId(), account.getBankCode(),
                bankNames.get(account.getBankCode()), account.getBranch(), account.getAccountName(),
                AccountNumberCipher.mask(account.getAccountLast4()), account.isActive(), account.getCreatedAt(),
                account.getUpdatedAt());
    }

    /** Audit view: the account number is masked. */
    private static Map<String, Object> snapshot(StoreBankAccount account) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("publicId", account.getPublicId());
        state.put("bankCode", account.getBankCode());
        state.put("branch", account.getBranch());
        state.put("accountName", account.getAccountName());
        state.put("accountNumber", AccountNumberCipher.mask(account.getAccountLast4()));
        state.put("active", account.isActive());
        return state;
    }
}
