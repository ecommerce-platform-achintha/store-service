package com.achintha.storeservice.events;

import com.achintha.storeservice.fee.FeeLedgerService;
import com.achintha.storeservice.outbox.ProcessedEventStore;
import com.achintha.storeservice.review.ReviewService;
import com.achintha.storeservice.score.MerchantScoreService;
import com.achintha.storeservice.score.ScoreEventType;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies one {@code order-events} message: merchant score deltas, metrics, the {@code ORDER_FEE} ledger line and
 * review eligibility. Idempotent: the event id is recorded in {@code processed_events} in the same transaction, and
 * score deltas and fees are additionally unique per order.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventHandler {

    public static final String CONSUMER = "store-service.order-events";

    /** Event types that, on their own, mean a penalty (in case order-service sends no explicit hint). */
    private static final Map<String, ScoreEventType> PENALTY_EVENTS = Map.of(
            "OrderShipmentOverdue", ScoreEventType.LATE_SHIPMENT,
            "OrderVerificationOverdue", ScoreEventType.LATE_VERIFICATION);

    private final ProcessedEventStore processedEvents;
    private final MerchantScoreService scoreService;
    private final FeeLedgerService feeLedgerService;
    private final ReviewService reviewService;

    /** @return {@code false} for a duplicate or an event without the ids it needs */
    @Transactional
    public boolean handle(OrderEvent event) {
        if (event.eventId() == null || event.eventType() == null || event.storeId() == null) {
            log.warn("Skipping order event without eventId/eventType/storeId");
            return false;
        }
        if (!processedEvents.markProcessed(event.eventId(), CONSUMER)) {
            return false;
        }
        switch (event.eventType()) {
            case "OrderPlaced" -> scoreService.orderReceived(event.storeId());
            case "OrderQuoted" -> {
                if (event.quoteRevision() == null || event.quoteRevision() <= 1) {
                    scoreService.orderQuoted(event.storeId(), responseSeconds(event));
                }
            }
            case "OrderCancelled" -> onCancelled(event);
            case "OrderCompleted" -> onCompleted(event);
            case "ComplaintDecided" -> {
                if ("UPHELD".equals(event.decision())) {
                    penalize(event, ScoreEventType.COMPLAINT_UPHELD);
                }
            }
            default -> {
                ScoreEventType penalty = PENALTY_EVENTS.get(event.eventType());
                if (penalty != null) {
                    penalize(event, penalty);
                }
            }
        }
        applyPenaltyHint(event);
        return true;
    }

    private void onCancelled(OrderEvent event) {
        scoreService.orderCancelled(event.storeId());
        String terminal = event.terminalStatus() != null ? event.terminalStatus() : event.status();
        if ("REJECTED_BY_MERCHANT".equals(terminal)) {
            scoreService.orderRejected(event.storeId(), responseSeconds(event));
        } else if ("EXPIRED_MERCHANT".equals(terminal)) {
            penalize(event, ScoreEventType.RESPONSE_TIMEOUT);
        }
    }

    private void onCompleted(OrderEvent event) {
        scoreService.applyScore(event.storeId(), event.orderId(), event.orderPublicId(),
                ScoreEventType.ORDER_COMPLETED, event.eventId());
        scoreService.orderCompleted(event.storeId(), event.grandTotal());
        if (event.orderId() == null || event.orderPublicId() == null) {
            log.warn("OrderCompleted {} has no order id: no fee line or review eligibility", event.eventId());
            return;
        }
        feeLedgerService.recordOrderFee(event.storeId(), event.orderId(), event.orderPublicId());
        if (event.customerId() != null) {
            reviewService.recordEligibility(event.orderId(), event.orderPublicId(), event.storeId(),
                    event.customerId(), event.occurredAt() != null ? event.occurredAt() : Instant.now());
        }
    }

    private void applyPenaltyHint(OrderEvent event) {
        if (event.merchantPenalty() == null) {
            return;
        }
        try {
            penalize(event, ScoreEventType.valueOf(event.merchantPenalty()));
        } catch (IllegalArgumentException e) {
            log.warn("Unknown merchantPenalty '{}' on event {}", event.merchantPenalty(), event.eventId());
        }
    }

    private void penalize(OrderEvent event, ScoreEventType type) {
        if (type == ScoreEventType.ORDER_COMPLETED) {
            return;
        }
        scoreService.applyScore(event.storeId(), event.orderId(), event.orderPublicId(), type, event.eventId());
    }

    private static Long responseSeconds(OrderEvent event) {
        if (event.placedAt() == null || event.occurredAt() == null) {
            return null;
        }
        return Duration.between(event.placedAt(), event.occurredAt()).toSeconds();
    }
}
