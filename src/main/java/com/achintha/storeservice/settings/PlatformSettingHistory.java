package com.achintha.storeservice.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One change of a setting (append-only in practice: nothing updates or deletes these rows). */
@Entity
@Table(name = "platform_settings_history")
@Getter
@Setter
@NoArgsConstructor
public class PlatformSettingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", nullable = false, length = 100)
    private String key;

    @Column(name = "old_value", length = 100)
    private String oldValue;

    @Column(name = "new_value", nullable = false, length = 100)
    private String newValue;

    @Column(name = "changed_by", nullable = false, length = 40)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(length = 500)
    private String reason;
}
