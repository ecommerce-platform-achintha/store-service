package com.achintha.storeservice.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.achintha.storeservice.exception.ApiException;
import org.junit.jupiter.api.Test;

class SettingValidatorTest {

    @Test
    void everyDefaultIsValidAndCanonical() {
        for (SettingKey key : SettingKey.values()) {
            assertThat(SettingValidator.normalize(key, key.defaultValue())).as(key.key()).isEqualTo(key.defaultValue());
        }
    }

    @Test
    void normalizesValues() {
        assertThat(SettingValidator.normalize(SettingKey.TIMERS_SHIP_BY_HOURS, "48.0")).isEqualTo("48");
        assertThat(SettingValidator.normalize(SettingKey.FEES_MONTHLY_LKR, "750")).isEqualTo("750.00");
        assertThat(SettingValidator.normalize(SettingKey.FEES_ENFORCED, "FALSE")).isEqualTo("false");
        assertThat(SettingValidator.normalize(SettingKey.SCORE_MERCHANT_AT_RISK_THRESHOLD, "-10")).isEqualTo("-10");
    }

    @Test
    void refusesOutOfRangeValues() {
        assertThatThrownBy(() -> SettingValidator.normalize(SettingKey.SCORE_MERCHANT_COMPLETED, "-1"))
                .isInstanceOf(ApiException.class).hasMessageContaining("between 0 and 100");
        assertThatThrownBy(() -> SettingValidator.normalize(SettingKey.TIMERS_AUTO_COMPLETE_DAYS, " "))
                .isInstanceOf(ApiException.class);
    }
}
