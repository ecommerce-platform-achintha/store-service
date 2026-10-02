package com.achintha.storeservice.fee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A credit granted to a store by the super admin. */
@Entity
@Table(name = "merchant_credits")
@Getter
@Setter
@NoArgsConstructor
public class MerchantCredit {

    @Id
    private UUID id;

    @Column(name = "public_id", nullable = false, unique = true, length = 20)
    private String publicId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "granted_by_user_id", nullable = false)
    private UUID grantedByUserId;

    @Column(name = "granted_by_public_id", length = 40)
    private String grantedByPublicId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
