package com.achintha.storeservice.store;

import com.achintha.storeservice.security.UserStatus;
import java.math.BigDecimal;
import java.time.Instant;

/** Response bodies. Public responses never contain UUIDs, contact details, owner data or bank data. */
public final class StoreResponses {

    private StoreResponses() {
    }

    /** Public listing/detail: only what a shopper needs. */
    public record PublicStoreResponse(
            String publicId,
            String name,
            String slug,
            String description,
            String city,
            String district,
            String logoUrl,
            BigDecimal ratingAvg,
            int ratingCount,
            boolean codEnabled) {
    }

    /**
     * The merchant's own view. {@code acceptingOrders} and the owner status are left out on purpose: a store in
     * {@code BAN_GRACE} must look unchanged to its merchant (D4).
     */
    public record MerchantStoreResponse(
            String publicId,
            String name,
            String slug,
            String description,
            String contactPhone,
            String contactEmail,
            String addressLine,
            String city,
            String district,
            String logoKey,
            String logoUrl,
            boolean codEnabled,
            boolean published,
            boolean profileComplete,
            BigDecimal ratingAvg,
            int ratingCount,
            Instant createdAt,
            Instant updatedAt) {
    }

    /** Admin view: includes the real owner status and both visibility flags. */
    public record AdminStoreResponse(
            String publicId,
            String name,
            String slug,
            String contactPhone,
            String contactEmail,
            String district,
            UserStatus ownerStatus,
            boolean published,
            boolean acceptingOrders,
            boolean codEnabled,
            BigDecimal ratingAvg,
            int ratingCount,
            Integer score,
            Instant createdAt,
            Instant updatedAt) {
    }
}
