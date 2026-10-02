package com.achintha.storeservice.score;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantScoreEventRepository extends JpaRepository<MerchantScoreEvent, Long> {

    boolean existsByStoreIdAndOrderIdAndEventType(UUID storeId, UUID orderId, ScoreEventType eventType);

    Page<MerchantScoreEvent> findAllByStoreId(UUID storeId, Pageable pageable);
}
