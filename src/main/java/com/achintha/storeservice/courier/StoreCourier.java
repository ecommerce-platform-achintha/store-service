package com.achintha.storeservice.courier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A courier the store ships with. */
@Entity
@Table(name = "store_couriers")
@Getter
@Setter
@NoArgsConstructor
public class StoreCourier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "courier_code", nullable = false, length = 30)
    private String courierCode;

    @Column(name = "enabled_at", nullable = false)
    private Instant enabledAt;
}
