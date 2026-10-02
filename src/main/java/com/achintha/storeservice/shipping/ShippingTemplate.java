package com.achintha.storeservice.shipping;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A store's shipping price template for one courier (at most one per courier). */
@Entity
@Table(name = "shipping_templates")
@Getter
@Setter
@NoArgsConstructor
public class ShippingTemplate {

    @Id
    private UUID id;

    @Column(name = "public_id", nullable = false, unique = true, length = 20)
    private String publicId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "courier_code", nullable = false, length = 30)
    private String courierCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 20)
    private ShippingRuleType ruleType;

    @Column(name = "base_amount", precision = 12, scale = 2)
    private BigDecimal baseAmount;

    @Convert(converter = ShippingRatesConverter.class)
    @Column(nullable = false, columnDefinition = "text")
    private List<ShippingRate> rates = List.of();

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;
}
