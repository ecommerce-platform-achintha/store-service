package com.achintha.storeservice.courier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Courier master data (super admin), addressed by its short code, e.g. {@code DOMEX}. */
@Entity
@Table(name = "couriers")
@Getter
@Setter
@NoArgsConstructor
public class Courier {

    /** Placeholder in {@link #trackingUrlTemplate} replaced by the (URL-encoded) tracking number. */
    public static final String TRACKING_PLACEHOLDER = "{trackingNumber}";

    @Id
    @Column(length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "tracking_url_template", nullable = false, length = 500)
    private String trackingUrlTemplate;

    @Column(name = "tracking_number_regex", nullable = false, length = 200)
    private String trackingNumberRegex;

    @Column(name = "cod_supported", nullable = false)
    private boolean codSupported;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
