package com.achintha.storeservice.fee;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeeLedgerRepository extends JpaRepository<FeeLedgerEntry, Long> {

    Page<FeeLedgerEntry> findAllByStoreId(UUID storeId, Pageable pageable);

    long countByOrderIdAndEntryType(UUID orderId, LedgerEntryType entryType);

    @Query("select coalesce(sum(e.amount), 0) from FeeLedgerEntry e where e.storeId = :storeId")
    BigDecimal balance(@Param("storeId") UUID storeId);
}
