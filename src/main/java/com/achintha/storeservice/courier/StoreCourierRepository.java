package com.achintha.storeservice.courier;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreCourierRepository extends JpaRepository<StoreCourier, Long> {

    List<StoreCourier> findAllByStoreIdOrderByCourierCodeAsc(UUID storeId);

    boolean existsByStoreIdAndCourierCode(UUID storeId, String courierCode);

    boolean existsByCourierCode(String courierCode);
}
