package com.achintha.storeservice.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The fields store-service reads from {@code order-events} messages (section 9). Unknown fields are ignored, so
 * order-service may send more.
 *
 * @param terminalStatus  {@code OrderCancelled}: e.g. {@code REJECTED_BY_MERCHANT}, {@code EXPIRED_MERCHANT}
 * @param grandTotal      {@code OrderCompleted}: revenue (LKR)
 * @param placedAt        when the order was placed, to measure the merchant's response time
 * @param quoteRevision   {@code OrderQuoted}: 1 for the first quote; re-quotes are not new responses
 * @param merchantPenalty penalty hint on any event: {@code RESPONSE_TIMEOUT}, {@code LATE_SHIPMENT},
 *                        {@code LATE_VERIFICATION}, {@code COMPLAINT_UPHELD}
 * @param decision        {@code ComplaintDecided}: {@code UPHELD} or {@code DISMISSED}
 */
public record OrderEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID orderId,
        String orderPublicId,
        UUID storeId,
        String storePublicId,
        UUID customerId,
        String status,
        String terminalStatus,
        String reason,
        BigDecimal grandTotal,
        Instant placedAt,
        Integer quoteRevision,
        String merchantPenalty,
        String decision) {
}
