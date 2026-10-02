package com.achintha.storeservice.store;

import com.achintha.storeservice.security.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A merchant's store (1 merchant = 1 store). {@code id} is the merchant's {@code storeId} from the JWT, assigned by
 * user-service at registration; the row is created when the merchant first saves the profile.
 *
 * <p>Visibility (section 4): {@code published} = profile complete and the owner {@code ACTIVE} (it stays true during
 * {@code BAN_GRACE} so the merchant notices nothing); {@code acceptingOrders} = published and the owner not in
 * {@code BAN_GRACE}. A store is public only when both are true.
 */
@Entity
@Table(name = "stores")
@Getter
@Setter
@NoArgsConstructor
public class Store {

    @Id
    private UUID id;

    @Column(name = "public_id", nullable = false, unique = true, length = 20)
    private String publicId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(length = 2000)
    private String description;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    @Column(name = "address_line", length = 300)
    private String addressLine;

    @Column(length = 100)
    private String city;

    @Column(length = 50)
    private String district;

    @Column(name = "logo_key", length = 200)
    private String logoKey;

    @Column(nullable = false)
    private boolean published;

    @Column(name = "accepting_orders", nullable = false)
    private boolean acceptingOrders;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_status", nullable = false, length = 32)
    private UserStatus ownerStatus;

    @Column(name = "cod_enabled", nullable = false)
    private boolean codEnabled;

    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    /** Listed publicly and able to take new orders. */
    public boolean isVisible() {
        return published && acceptingOrders;
    }

    /** Everything a customer needs before the store can be published. */
    public boolean isProfileComplete() {
        return notBlank(name) && notBlank(contactPhone) && notBlank(contactEmail) && notBlank(addressLine)
                && notBlank(district);
    }

    /**
     * Recomputes {@code published} and {@code acceptingOrders} from the owner status and the profile.
     *
     * @return whether either flag changed
     */
    public boolean recomputeVisibility() {
        boolean wasPublished = published;
        boolean wasAccepting = acceptingOrders;
        published = isProfileComplete()
                && (ownerStatus == UserStatus.ACTIVE || ownerStatus == UserStatus.BAN_GRACE);
        acceptingOrders = published && ownerStatus == UserStatus.ACTIVE;
        return wasPublished != published || wasAccepting != acceptingOrders;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
