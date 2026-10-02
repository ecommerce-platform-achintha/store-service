package com.achintha.storeservice.settings;

import com.achintha.storeservice.common.Validation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class SettingsDtos {

    private SettingsDtos() {
    }

    /** @param value the new value as text, e.g. {@code "48"}, {@code "12.50"}, {@code "false"} */
    public record UpdateSettingRequest(
            @NotBlank @Size(max = 50) String value,
            @Size(max = Validation.REASON_MAX) String reason) {
    }

    public record SettingResponse(
            String key,
            String value,
            SettingValueType type,
            String defaultValue,
            BigDecimal min,
            BigDecimal max,
            Instant updatedAt,
            String updatedBy) {
    }

    public record SettingHistoryResponse(
            String key,
            String oldValue,
            String newValue,
            String changedBy,
            Instant changedAt,
            String reason) {

        static SettingHistoryResponse from(PlatformSettingHistory history) {
            return new SettingHistoryResponse(history.getKey(), history.getOldValue(), history.getNewValue(),
                    history.getChangedBy(), history.getChangedAt(), history.getReason());
        }
    }

    /** Internal view for other services: key, typed-as-text value and type. */
    public record InternalSetting(String key, String value, SettingValueType type) {
    }
}
