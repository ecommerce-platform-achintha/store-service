package com.achintha.storeservice.outbox;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JSON payload of every {@code store-events} message, keyed by {@code aggregateId} (the store's UUID for
 * {@code StoreVisibilityChanged}). Fields that do not apply to an event are omitted. No bank data, ever.
 *
 * @param visible        {@code published && acceptingOrders}: the store may be listed and may take new orders
 * @param settingKey     {@code SettingsChanged} only
 * @param holidayDate    {@code HolidaysChanged} only
 * @param change         what happened, e.g. {@code OWNER_BAN_GRACE}, {@code CREATED}, {@code DELETED}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StoreEvent(
        UUID eventId,
        StoreEventType eventType,
        Instant occurredAt,
        UUID aggregateId,
        UUID storeId,
        String storePublicId,
        String storeName,
        Boolean published,
        Boolean acceptingOrders,
        Boolean visible,
        Boolean codEnabled,
        String settingKey,
        String settingValue,
        LocalDate holidayDate,
        String change) {
}
