package com.achintha.storeservice.review;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A customer's review of a store for one completed order (one per order). */
@Entity
@Table(name = "store_reviews")
@Getter
@Setter
@NoArgsConstructor
public class StoreReview {

    @Id
    private UUID id;

    @Column(name = "public_id", nullable = false, unique = true, length = 20)
    private String publicId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "order_public_id", nullable = false, length = 20)
    private String orderPublicId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private short rating;

    @Column(length = 2000)
    private String body;

    @Convert(converter = StringListConverter.class)
    @Column(name = "attachment_keys", nullable = false, columnDefinition = "text")
    private List<String> attachmentKeys = List.of();

    @Column(nullable = false)
    private boolean hidden;

    @Column(name = "hidden_reason", length = 500)
    private String hiddenReason;

    @Column(name = "hidden_by", length = 40)
    private String hiddenBy;

    @Column(name = "hidden_at")
    private Instant hiddenAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;
}
