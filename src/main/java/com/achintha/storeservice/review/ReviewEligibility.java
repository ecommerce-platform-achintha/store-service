package com.achintha.storeservice.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A completed order its customer may review the store for (filled from {@code OrderCompleted}). */
@Entity
@Table(name = "review_eligibility")
@Getter
@Setter
@NoArgsConstructor
public class ReviewEligibility {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "order_public_id", nullable = false, unique = true, length = 20)
    private String orderPublicId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;
}
