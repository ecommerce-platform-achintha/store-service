package com.achintha.storeservice.bank;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankRepository extends JpaRepository<Bank, String> {

    List<Bank> findAllByActiveTrueOrderByNameAsc();

    Page<Bank> findAllByActive(boolean active, Pageable pageable);
}
