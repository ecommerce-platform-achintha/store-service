package com.achintha.storeservice.fee;

import com.achintha.storeservice.audit.AuditService;
import com.achintha.storeservice.common.PageResponse;
import com.achintha.storeservice.common.PublicIdGenerator;
import com.achintha.storeservice.common.TextSanitizer;
import com.achintha.storeservice.exception.ApiException;
import com.achintha.storeservice.exception.ErrorCode;
import com.achintha.storeservice.fee.FeeDtos.CreditResponse;
import com.achintha.storeservice.fee.FeeDtos.LedgerEntryResponse;
import com.achintha.storeservice.fee.FeeDtos.LedgerResponse;
import com.achintha.storeservice.security.Actor;
import com.achintha.storeservice.settings.PlatformSettingsService;
import com.achintha.storeservice.settings.SettingKey;
import com.achintha.storeservice.store.Store;
import com.achintha.storeservice.store.StoreQueryService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform fee ledger (D11): an {@code ORDER_FEE} line of {@code fees.per-order-lkr} per completed order and
 * super-admin credits. Nothing is invoiced or enforced ({@code fees.enforced=false}).
 */
@Service
@RequiredArgsConstructor
public class FeeLedgerService {

    private final FeeLedgerRepository ledgerRepository;
    private final MerchantCreditRepository creditRepository;
    private final StoreQueryService storeQueryService;
    private final PlatformSettingsService settings;
    private final PublicIdGenerator publicIds;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    /**
     * Records the per-order fee once per order (unique index; a re-delivered event inserts nothing).
     *
     * @return whether a line was written
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean recordOrderFee(UUID storeId, UUID orderId, String orderPublicId) {
        BigDecimal fee = settings.decimalValue(SettingKey.FEES_PER_ORDER_LKR);
        int inserted = jdbcTemplate.update("""
                insert into fee_ledger (store_id, entry_type, amount, order_id, order_public_id, description,
                                        created_at)
                values (?, 'ORDER_FEE', ?, ?, ?, ?, ?)
                on conflict do nothing
                """, storeId, fee, orderId, orderPublicId, "Platform fee for order " + orderPublicId,
                Timestamp.from(clock.instant()));
        return inserted == 1;
    }

    @Transactional
    public CreditResponse grantCredit(Actor actor, String storePublicId, BigDecimal amount, String rawReason) {
        Store store = storeQueryService.requireAny(storePublicId);
        String reason = TextSanitizer.cleanLine(rawReason);
        if (reason == null) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "reason must not be blank");
        }
        Instant now = clock.instant();
        MerchantCredit credit = new MerchantCredit();
        credit.setId(UUID.randomUUID());
        credit.setPublicId(publicIds.generateUnique(PublicIdGenerator.CREDIT_PREFIX, creditRepository::existsByPublicId));
        credit.setStoreId(store.getId());
        credit.setAmount(amount);
        credit.setReason(reason);
        credit.setGrantedByUserId(actor.id());
        credit.setGrantedByPublicId(actor.publicId());
        credit.setCreatedAt(now);
        credit = creditRepository.save(credit);

        FeeLedgerEntry entry = new FeeLedgerEntry();
        entry.setStoreId(store.getId());
        entry.setEntryType(LedgerEntryType.CREDIT);
        entry.setAmount(amount.negate());
        entry.setCreditId(credit.getId());
        entry.setDescription("Credit " + credit.getPublicId() + ": " + reason);
        entry.setCreatedAt(now);
        ledgerRepository.saveAndFlush(entry);

        auditService.record(actor, "MERCHANT_CREDIT_GRANTED", AuditService.TARGET_CREDIT, credit.getPublicId(), null,
                Map.of("store", store.getPublicId(), "amount", amount), reason);
        return new CreditResponse(credit.getPublicId(), store.getPublicId(), amount, reason, now,
                ledgerRepository.balance(store.getId()));
    }

    @Transactional(readOnly = true)
    public LedgerResponse ledger(String storePublicId, Pageable pageable) {
        Store store = storeQueryService.requireAny(storePublicId);
        return new LedgerResponse(store.getPublicId(), ledgerRepository.balance(store.getId()),
                settings.booleanValue(SettingKey.FEES_ENFORCED),
                PageResponse.from(ledgerRepository.findAllByStoreId(store.getId(), pageable),
                        LedgerEntryResponse::from));
    }
}
