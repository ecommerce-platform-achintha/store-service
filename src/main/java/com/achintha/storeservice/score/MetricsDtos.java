package com.achintha.storeservice.score;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public final class MetricsDtos {

    private MetricsDtos() {
    }

    /**
     * Store metrics (owner and admins).
     *
     * @param acceptanceRatePercent       quoted / received orders
     * @param averageResponseMinutes      time from placement to the merchant's first answer (quote or rejection)
     * @param cancellationRatePercent     cancelled / received orders (every cancellation, whoever caused it)
     * @param revenue                     LKR, grand totals of completed orders
     */
    public record MetricsResponse(
            int score,
            int ordersReceived,
            int ordersQuoted,
            int ordersRejected,
            BigDecimal acceptanceRatePercent,
            BigDecimal averageResponseMinutes,
            int cancelledOrders,
            BigDecimal cancellationRatePercent,
            int lateShipments,
            int lateVerifications,
            int complaintsUpheld,
            int completedOrders,
            BigDecimal revenue,
            Instant updatedAt) {

        public static MetricsResponse from(MerchantMetrics m) {
            return new MetricsResponse(m.getScore(), m.getOrdersReceived(), m.getOrdersQuoted(),
                    m.getOrdersRejected(), percent(m.getOrdersQuoted(), m.getOrdersReceived()),
                    m.getResponseCount() == 0 ? null
                            : BigDecimal.valueOf(m.getTotalResponseSeconds())
                            .divide(BigDecimal.valueOf(m.getResponseCount() * 60L), 1, RoundingMode.HALF_UP),
                    m.getCancelledOrders(), percent(m.getCancelledOrders(), m.getOrdersReceived()),
                    m.getLateShipments(), m.getLateVerifications(), m.getComplaintsUpheld(), m.getCompletedOrders(),
                    m.getRevenue(), m.getUpdatedAt());
        }

        private static BigDecimal percent(int part, int whole) {
            return whole == 0 ? null : BigDecimal.valueOf(part * 100L)
                    .divide(BigDecimal.valueOf(whole), 2, RoundingMode.HALF_UP);
        }
    }

    public record AtRiskMerchantResponse(
            String storePublicId,
            String storeName,
            int score,
            int threshold,
            Instant atRiskSince) {
    }

    public record ScoreEventResponse(
            ScoreEventType type,
            int delta,
            int scoreAfter,
            String orderPublicId,
            Instant createdAt) {

        public static ScoreEventResponse from(MerchantScoreEvent event) {
            return new ScoreEventResponse(event.getEventType(), event.getDelta(), event.getScoreAfter(),
                    event.getOrderPublicId(), event.getCreatedAt());
        }
    }
}
