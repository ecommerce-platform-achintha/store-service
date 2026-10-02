package com.achintha.storeservice.review;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewEligibilityRepository extends JpaRepository<ReviewEligibility, UUID> {

    /** Scoped by customer and store: someone else's order is simply not found. */
    Optional<ReviewEligibility> findByOrderPublicIdAndCustomerIdAndStoreId(String orderPublicId, UUID customerId,
                                                                           UUID storeId);
}
