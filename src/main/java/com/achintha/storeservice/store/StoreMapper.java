package com.achintha.storeservice.store;

import com.achintha.storeservice.ports.ImageStoragePort;
import com.achintha.storeservice.store.StoreResponses.AdminStoreResponse;
import com.achintha.storeservice.store.StoreResponses.MerchantStoreResponse;
import com.achintha.storeservice.store.StoreResponses.PublicStoreResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StoreMapper {

    private final ImageStoragePort imageStorage;

    public PublicStoreResponse toPublic(Store store) {
        return new PublicStoreResponse(store.getPublicId(), store.getName(), store.getSlug(), store.getDescription(),
                store.getCity(), store.getDistrict(), imageStorage.urlFor(store.getLogoKey()), store.getRatingAvg(),
                store.getRatingCount(), store.isCodEnabled());
    }

    public MerchantStoreResponse toMerchant(Store store) {
        return new MerchantStoreResponse(store.getPublicId(), store.getName(), store.getSlug(),
                store.getDescription(), store.getContactPhone(), store.getContactEmail(), store.getAddressLine(),
                store.getCity(), store.getDistrict(), store.getLogoKey(), imageStorage.urlFor(store.getLogoKey()),
                store.isCodEnabled(), store.isPublished(), store.isProfileComplete(), store.getRatingAvg(),
                store.getRatingCount(), store.getCreatedAt(), store.getUpdatedAt());
    }

    public AdminStoreResponse toAdmin(Store store, Integer score) {
        return new AdminStoreResponse(store.getPublicId(), store.getName(), store.getSlug(), store.getContactPhone(),
                store.getContactEmail(), store.getDistrict(), store.getOwnerStatus(), store.isPublished(),
                store.isAcceptingOrders(), store.isCodEnabled(), store.getRatingAvg(), store.getRatingCount(), score,
                store.getCreatedAt(), store.getUpdatedAt());
    }
}
