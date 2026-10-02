package com.achintha.storeservice.score;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantMetricsRepository extends JpaRepository<MerchantMetrics, UUID> {

    /** Row lock: events of one store are applied one after the other, even across consumer threads/replicas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MerchantMetrics m where m.storeId = :storeId")
    Optional<MerchantMetrics> findForUpdate(@Param("storeId") UUID storeId);

    @Query("select m from MerchantMetrics m where m.score < :threshold")
    Page<MerchantMetrics> findAtRisk(@Param("threshold") int threshold, Pageable pageable);
}
