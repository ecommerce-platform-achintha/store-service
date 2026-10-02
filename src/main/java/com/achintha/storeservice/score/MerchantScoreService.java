package com.achintha.storeservice.score;

import com.achintha.storeservice.ports.NotificationPort;
import com.achintha.storeservice.settings.PlatformSettingsService;
import com.achintha.storeservice.settings.SettingKey;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Merchant score and metrics, updated from {@code order-events} (in the consumer's transaction). Deltas come from the
 * platform settings; a score change applies at most once per (store, order, type). When the score falls below
 * {@code score.merchant.at-risk-threshold}, the store is flagged at risk and admins are alerted (D2: nothing
 * automatic happens to the merchant).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantScoreService {

    private final MerchantMetricsRepository metricsRepository;
    private final MerchantScoreEventRepository scoreEventRepository;
    private final PlatformSettingsService settings;
    private final NotificationPort notifications;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    /**
     * @return {@code false} if this change was already applied for the order
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean applyScore(UUID storeId, UUID orderId, String orderPublicId, ScoreEventType type,
                              UUID sourceEventId) {
        if (orderId != null && scoreEventRepository.existsByStoreIdAndOrderIdAndEventType(storeId, orderId, type)) {
            return false;
        }
        int delta = settings.intValue(type.deltaSetting());
        Instant now = clock.instant();
        MerchantMetrics metrics = lock(storeId, now);
        int before = metrics.getScore();
        metrics.setScore(before + delta);
        switch (type) {
            case LATE_SHIPMENT -> metrics.setLateShipments(metrics.getLateShipments() + 1);
            case LATE_VERIFICATION -> metrics.setLateVerifications(metrics.getLateVerifications() + 1);
            case COMPLAINT_UPHELD -> metrics.setComplaintsUpheld(metrics.getComplaintsUpheld() + 1);
            default -> {
                // completion and response timeouts are counted by the order metrics
            }
        }
        checkAtRisk(metrics, storeId, now);
        metrics.setUpdatedAt(now);

        MerchantScoreEvent event = new MerchantScoreEvent();
        event.setStoreId(storeId);
        event.setOrderId(orderId);
        event.setOrderPublicId(orderPublicId);
        event.setEventType(type);
        event.setDelta(delta);
        event.setScoreAfter(metrics.getScore());
        event.setSourceEventId(sourceEventId);
        event.setCreatedAt(now);
        scoreEventRepository.save(event);
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void orderReceived(UUID storeId) {
        update(storeId, m -> m.setOrdersReceived(m.getOrdersReceived() + 1));
    }

    /** @param responseSeconds time from placement to this first answer, if known */
    @Transactional(propagation = Propagation.MANDATORY)
    public void orderQuoted(UUID storeId, Long responseSeconds) {
        update(storeId, m -> {
            m.setOrdersQuoted(m.getOrdersQuoted() + 1);
            addResponse(m, responseSeconds);
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void orderRejected(UUID storeId, Long responseSeconds) {
        update(storeId, m -> {
            m.setOrdersRejected(m.getOrdersRejected() + 1);
            addResponse(m, responseSeconds);
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void orderCancelled(UUID storeId) {
        update(storeId, m -> m.setCancelledOrders(m.getCancelledOrders() + 1));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void orderCompleted(UUID storeId, BigDecimal grandTotal) {
        update(storeId, m -> {
            m.setCompletedOrders(m.getCompletedOrders() + 1);
            if (grandTotal != null) {
                m.setRevenue(m.getRevenue().add(grandTotal));
            }
        });
    }

    // ------------------------------------------------------------------------------------------------- helpers

    private void update(UUID storeId, Consumer<MerchantMetrics> change) {
        Instant now = clock.instant();
        MerchantMetrics metrics = lock(storeId, now);
        change.accept(metrics);
        metrics.setUpdatedAt(now);
    }

    /** The store's metrics row, created if missing (race-free), and row-locked until the transaction ends. */
    private MerchantMetrics lock(UUID storeId, Instant now) {
        jdbcTemplate.update("insert into merchant_metrics (store_id, updated_at) values (?, ?) "
                + "on conflict (store_id) do nothing", storeId, Timestamp.from(now));
        return metricsRepository.findForUpdate(storeId).orElseThrow();
    }

    private static void addResponse(MerchantMetrics metrics, Long responseSeconds) {
        if (responseSeconds != null && responseSeconds >= 0) {
            metrics.setResponseCount(metrics.getResponseCount() + 1);
            metrics.setTotalResponseSeconds(metrics.getTotalResponseSeconds() + responseSeconds);
        }
    }

    private void checkAtRisk(MerchantMetrics metrics, UUID storeId, Instant now) {
        int threshold = settings.intValue(SettingKey.SCORE_MERCHANT_AT_RISK_THRESHOLD);
        if (metrics.getScore() < threshold && metrics.getAtRiskSince() == null) {
            metrics.setAtRiskSince(now);
            log.info("Store {} is at risk: merchant score {} is below {}", storeId, metrics.getScore(), threshold);
            notifications.notifyAdmins("MERCHANT_AT_RISK",
                    "A merchant's score fell below " + threshold + " (see GET /api/admin/merchants/at-risk)");
        } else if (metrics.getScore() >= threshold && metrics.getAtRiskSince() != null) {
            metrics.setAtRiskSince(null);
        }
    }
}
