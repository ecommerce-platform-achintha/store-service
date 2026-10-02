package com.achintha.storeservice.shipping;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every merchant query is scoped by store (BOLA). */
public interface ShippingTemplateRepository extends JpaRepository<ShippingTemplate, UUID> {

    Optional<ShippingTemplate> findByPublicIdAndStoreId(String publicId, UUID storeId);

    Optional<ShippingTemplate> findByStoreIdAndCourierCode(UUID storeId, String courierCode);

    Page<ShippingTemplate> findAllByStoreId(UUID storeId, Pageable pageable);

    boolean existsByStoreIdAndCourierCode(UUID storeId, String courierCode);

    boolean existsByCourierCode(String courierCode);

    boolean existsByPublicId(String publicId);
}
