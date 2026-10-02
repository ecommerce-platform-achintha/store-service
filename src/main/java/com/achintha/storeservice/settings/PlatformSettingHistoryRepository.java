package com.achintha.storeservice.settings;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSettingHistoryRepository extends JpaRepository<PlatformSettingHistory, Long> {

    Page<PlatformSettingHistory> findAllByKey(String key, Pageable pageable);
}
