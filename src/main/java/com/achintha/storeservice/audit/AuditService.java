package com.achintha.storeservice.audit;

import com.achintha.storeservice.security.Actor;
import java.time.Clock;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes audit rows for privileged mutations (admin, super admin, merchant owner, assistant, system), in the
 * caller's transaction so the row exists exactly when the change does. Callers pass snapshots with sensitive fields
 * already masked (bank account numbers are only ever recorded as {@code ****1234}).
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    public static final String TARGET_STORE = "STORE";
    public static final String TARGET_BANK = "BANK";
    public static final String TARGET_BANK_ACCOUNT = "BANK_ACCOUNT";
    public static final String TARGET_COURIER = "COURIER";
    public static final String TARGET_SHIPPING_TEMPLATE = "SHIPPING_TEMPLATE";
    public static final String TARGET_SETTING = "SETTING";
    public static final String TARGET_HOLIDAY = "HOLIDAY";
    public static final String TARGET_REVIEW = "REVIEW";
    public static final String TARGET_CREDIT = "CREDIT";

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Actor actor, String action, String targetType, String targetId,
                       Map<String, ?> before, Map<String, ?> after, String reason) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.setOccurredAt(clock.instant());
        entry.setActorId(actor.id());
        entry.setActorPublicId(actor.publicId() != null ? actor.publicId() : Actor.SYSTEM);
        entry.setActorRole(Actor.SYSTEM.equals(actor.publicId()) ? Actor.SYSTEM : actor.role().name());
        entry.setActorStoreId(actor.storeId());
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setBeforeState(before == null ? null : objectMapper.writeValueAsString(before));
        entry.setAfterState(after == null ? null : objectMapper.writeValueAsString(after));
        entry.setReason(reason);
        repository.save(entry);
    }
}
