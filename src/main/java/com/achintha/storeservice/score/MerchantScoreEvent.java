package com.achintha.storeservice.score;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One applied score change; at most one per (store, order, type) (unique index). */
@Entity
@Table(name = "merchant_score_events")
@Getter
@Setter
@NoArgsConstructor
public class MerchantScoreEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "order_public_id", length = 20)
    private String orderPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private ScoreEventType eventType;

    @Column(nullable = false)
    private int delta;

    @Column(name = "score_after", nullable = false)
    private int scoreAfter;

    @Column(name = "source_event_id")
    private UUID sourceEventId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
