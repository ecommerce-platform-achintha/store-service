package com.achintha.storeservice.internal;

import com.achintha.storeservice.store.Store;
import java.util.UUID;

/**
 * Store data for order-service (internal APIs may carry UUIDs, section 1).
 *
 * @param visible {@code published && acceptingOrders}: whether the store takes new orders right now
 */
public record InternalStoreResponse(
        UUID id,
        String publicId,
        String name,
        String slug,
        boolean published,
        boolean acceptingOrders,
        boolean visible,
        boolean codEnabled) {

    static InternalStoreResponse from(Store store) {
        return new InternalStoreResponse(store.getId(), store.getPublicId(), store.getName(), store.getSlug(),
                store.isPublished(), store.isAcceptingOrders(), store.isVisible(), store.isCodEnabled());
    }
}
