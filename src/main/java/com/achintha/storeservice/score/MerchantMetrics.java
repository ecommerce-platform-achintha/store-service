package com.achintha.storeservice.score;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Running merchant score and order metrics of one store, maintained from {@code order-events}. */
@Entity
@Table(name = "merchant_metrics")
@Getter
@Setter
@NoArgsConstructor
public class MerchantMetrics {

    @Id
    @Column(name = "store_id")
    private UUID storeId;

    @Column(nullable = false)
    private int score;

    @Column(name = "orders_received", nullable = false)
    private int ordersReceived;

    /** Orders the merchant answered with a quote (accepted). */
    @Column(name = "orders_quoted", nullable = false)
    private int ordersQuoted;

    @Column(name = "orders_rejected", nullable = false)
    private int ordersRejected;

    /** First responses (quote or rejection) with a known response time. */
    @Column(name = "response_count", nullable = false)
    private int responseCount;

    @Column(name = "total_response_seconds", nullable = false)
    private long totalResponseSeconds;

    @Column(name = "cancelled_orders", nullable = false)
    private int cancelledOrders;

    @Column(name = "late_shipments", nullable = false)
    private int lateShipments;

    @Column(name = "late_verifications", nullable = false)
    private int lateVerifications;

    @Column(name = "complaints_upheld", nullable = false)
    private int complaintsUpheld;

    @Column(name = "completed_orders", nullable = false)
    private int completedOrders;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal revenue = BigDecimal.ZERO;

    @Column(name = "at_risk_since")
    private Instant atRiskSince;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    public static MerchantMetrics empty(UUID storeId, Instant now) {
        MerchantMetrics metrics = new MerchantMetrics();
        metrics.setStoreId(storeId);
        metrics.setUpdatedAt(now);
        return metrics;
    }
}
