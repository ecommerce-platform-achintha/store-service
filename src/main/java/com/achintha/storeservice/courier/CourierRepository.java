package com.achintha.storeservice.courier;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourierRepository extends JpaRepository<Courier, String> {

    List<Courier> findAllByActiveTrueOrderByNameAsc();

    Page<Courier> findAllByActive(boolean active, Pageable pageable);
}
