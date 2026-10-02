package com.achintha.storeservice.holiday;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HolidayRepository extends JpaRepository<Holiday, UUID> {

    Optional<Holiday> findByPublicId(String publicId);

    boolean existsByPublicId(String publicId);

    boolean existsByDate(LocalDate date);

    Page<Holiday> findAllByDateBetween(LocalDate from, LocalDate to, Pageable pageable);

    List<Holiday> findAllByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);
}
