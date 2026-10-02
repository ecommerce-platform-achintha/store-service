package com.achintha.storeservice.bank;

import com.achintha.storeservice.audit.AuditService;
import com.achintha.storeservice.bank.BankDtos.BankResponse;
import com.achintha.storeservice.bank.BankDtos.CreateBankRequest;
import com.achintha.storeservice.bank.BankDtos.UpdateBankRequest;
import com.achintha.storeservice.common.PageResponse;
import com.achintha.storeservice.common.TextSanitizer;
import com.achintha.storeservice.exception.ApiException;
import com.achintha.storeservice.exception.ErrorCode;
import com.achintha.storeservice.exception.NotFoundException;
import com.achintha.storeservice.security.Actor;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bank master data. Writes are super-admin only and audited; a bank used by an account cannot be deleted. */
@Service
@RequiredArgsConstructor
public class BankService {

    private final BankRepository bankRepository;
    private final StoreBankAccountRepository accountRepository;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<BankResponse> list(Boolean active, Pageable pageable) {
        return PageResponse.from(active == null ? bankRepository.findAll(pageable)
                : bankRepository.findAllByActive(active, pageable), BankResponse::from);
    }

    @Transactional(readOnly = true)
    public List<BankResponse> activeBanks() {
        return bankRepository.findAllByActiveTrueOrderByNameAsc().stream().map(BankResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public BankResponse get(String code) {
        return BankResponse.from(require(code));
    }

    @Transactional
    public BankResponse create(Actor actor, CreateBankRequest request) {
        if (bankRepository.existsById(request.code())) {
            throw ApiException.conflict(ErrorCode.BANK_CODE_TAKEN, "A bank with this code already exists");
        }
        Instant now = clock.instant();
        Bank bank = new Bank();
        bank.setCode(request.code());
        bank.setName(requireName(request.name()));
        bank.setActive(request.active() == null || request.active());
        bank.setCreatedAt(now);
        bank.setUpdatedAt(now);
        bank = bankRepository.save(bank);
        auditService.record(actor, "BANK_CREATED", AuditService.TARGET_BANK, bank.getCode(), null, snapshot(bank),
                null);
        return BankResponse.from(bank);
    }

    @Transactional
    public BankResponse update(Actor actor, String code, UpdateBankRequest request) {
        Bank bank = require(code);
        Map<String, Object> before = snapshot(bank);
        bank.setName(requireName(request.name()));
        bank.setActive(request.active());
        bank.setUpdatedAt(clock.instant());
        auditService.record(actor, "BANK_UPDATED", AuditService.TARGET_BANK, bank.getCode(), before, snapshot(bank),
                null);
        return BankResponse.from(bank);
    }

    @Transactional
    public void delete(Actor actor, String code) {
        Bank bank = require(code);
        if (accountRepository.existsByBankCode(code)) {
            throw ApiException.conflict(ErrorCode.BANK_IN_USE,
                    "Store bank accounts use this bank; deactivate it instead");
        }
        bankRepository.delete(bank);
        auditService.record(actor, "BANK_DELETED", AuditService.TARGET_BANK, code, snapshot(bank), null, null);
    }

    /** An active bank, for new or changed store accounts. */
    @Transactional(readOnly = true)
    public Bank requireActive(String code) {
        return bankRepository.findById(code).filter(Bank::isActive).orElseThrow(() -> ApiException.badRequest(
                ErrorCode.BANK_NOT_AVAILABLE, "bankCode must be an active bank from GET .../bank-accounts/banks"));
    }

    private Bank require(String code) {
        return bankRepository.findById(code).orElseThrow(() -> new NotFoundException("Bank not found"));
    }

    private static String requireName(String raw) {
        String name = TextSanitizer.cleanLine(raw);
        if (name == null) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "name must not be blank");
        }
        return name;
    }

    private static Map<String, Object> snapshot(Bank bank) {
        return Map.of("code", bank.getCode(), "name", bank.getName(), "active", bank.isActive());
    }
}
