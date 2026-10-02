package com.achintha.storeservice.review;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreReviewRepository extends JpaRepository<StoreReview, UUID> {

    Optional<StoreReview> findByPublicId(String publicId);

    /** Merchant side: scoped by the JWT store, a foreign review is not found. */
    Optional<StoreReview> findByPublicIdAndStoreId(String publicId, UUID storeId);

    Page<StoreReview> findAllByStoreIdAndHiddenFalse(UUID storeId, Pageable pageable);

    Page<StoreReview> findAllByStoreId(UUID storeId, Pageable pageable);

    boolean existsByOrderId(UUID orderId);

    boolean existsByPublicId(String publicId);
}
