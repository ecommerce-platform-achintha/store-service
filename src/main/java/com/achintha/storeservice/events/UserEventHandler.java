package com.achintha.storeservice.events;

import com.achintha.storeservice.outbox.ProcessedEventStore;
import com.achintha.storeservice.security.Role;
import com.achintha.storeservice.security.SecurityStateCache;
import com.achintha.storeservice.security.UserStatus;
import com.achintha.storeservice.store.StoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies one {@code user-events} message: keeps the security-state cache current ({@code UserSecurityChanged},
 * {@code UserStatusChanged}) and reacts to a merchant's status ({@code BAN_GRACE}: no new orders, {@code BANNED}:
 * unpublished, {@code ACTIVE}: published again). Idempotent via {@code processed_events}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventHandler {

    public static final String CONSUMER = "store-service.user-events";

    private final ProcessedEventStore processedEvents;
    private final SecurityStateCache securityStateCache;
    private final StoreService storeService;

    /** @return {@code false} for a duplicate or an event this service does not use */
    @Transactional
    public boolean handle(UserEvent event) {
        if (event.eventId() == null || event.userId() == null || event.eventType() == null) {
            log.warn("Skipping user event without eventId/eventType/userId");
            return false;
        }
        UserStatus status = parseStatus(event.status());
        if ("UserSecurityChanged".equals(event.eventType()) || "UserStatusChanged".equals(event.eventType())) {
            // The cache is in memory: refresh it even for an already-processed event (another replica may have
            // recorded it, this one still needs the new version)
            securityStateCache.apply(event.userId(), event.tokenVersion(), status);
        }
        if (!processedEvents.markProcessed(event.eventId(), CONSUMER)) {
            return false;
        }
        if ("UserStatusChanged".equals(event.eventType()) && Role.ROLE_MERCHANT.name().equals(event.role())
                && event.storeId() != null && status != null) {
            storeService.applyOwnerStatus(event.storeId(), event.userId(), status);
        }
        return true;
    }

    private static UserStatus parseStatus(String status) {
        if (status == null) {
            return null;
        }
        try {
            return UserStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
