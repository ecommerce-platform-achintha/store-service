package com.achintha.storeservice.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.achintha.storeservice.audit.AuditLogEntry;
import com.achintha.storeservice.audit.AuditLogRepository;
import com.achintha.storeservice.outbox.OutboxMessage;
import com.achintha.storeservice.outbox.OutboxRepository;
import com.achintha.storeservice.support.IntegrationTest;
import com.achintha.storeservice.support.TestUser;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Platform settings (section 7): defaults, validated updates, history, audit, SettingsChanged and the cache. */
class PlatformSettingsIntegrationTest extends IntegrationTest {

    @Autowired
    private PlatformSettingRepository settingRepository;
    @Autowired
    private OutboxRepository outboxRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void restoreDefaults() {
        jdbcTemplate.update("update platform_settings set value = '2' where setting_key = 'orders.max-quote-revisions'");
        jdbcTemplate.update("update platform_settings set value = '10.00' where setting_key = 'fees.per-order-lkr'");
        settingsService.invalidate();
    }

    @Test
    void seededDefaultsMatchTheContract() {
        Map<String, PlatformSetting> stored = settingRepository.findAll().stream()
                .collect(Collectors.toMap(PlatformSetting::getKey, Function.identity()));
        assertThat(stored.keySet()).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(SettingKey.values()).map(SettingKey::key).toList());
        for (SettingKey key : SettingKey.values()) {
            if (stored.get(key.key()).getUpdatedBy().equals("SYSTEM")) {
                assertThat(stored.get(key.key()).getValueType()).isEqualTo(key.type());
            }
        }
        assertThat(SettingKey.SCORE_MERCHANT_RESPONSE_TIMEOUT.defaultValue()).isEqualTo("-2");
        assertThat(SettingKey.SCORE_MERCHANT_AT_RISK_THRESHOLD.defaultValue()).isEqualTo("-5");
        assertThat(SettingKey.FEES_PER_ORDER_LKR.defaultValue()).isEqualTo("10.00");
        assertThat(SettingKey.FEES_ENFORCED.defaultValue()).isEqualTo("false");
        assertThat(SettingKey.TIMERS_SHIP_BY_HOURS.defaultValue()).isEqualTo("72");
    }

    @Test
    void updateIsValidatedAuditedKeptInHistoryAndAnnounced() throws Exception {
        TestUser superAdmin = superAdmin();
        String token = tokenFor(superAdmin);
        long outboxBefore = outboxRepository.count();

        mockMvc.perform(json(withBearer(put("/api/super-admin/settings/orders.max-quote-revisions"), token),
                        "{\"value\":\" 3 \",\"reason\":\"More flexibility\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("3"))
                .andExpect(jsonPath("$.defaultValue").value("2"))
                .andExpect(jsonPath("$.updatedBy").value(superAdmin.publicId()));
        assertThat(settingsService.intValue(SettingKey.ORDERS_MAX_QUOTE_REVISIONS)).isEqualTo(3);

        mockMvc.perform(withBearer(get("/api/super-admin/settings/orders.max-quote-revisions/history"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].oldValue").value("2"))
                .andExpect(jsonPath("$.content[0].newValue").value("3"))
                .andExpect(jsonPath("$.content[0].reason").value("More flexibility"));

        List<OutboxMessage> events = outboxRepository.findAll().stream().skip(outboxBefore)
                .filter(m -> m.getEventType().equals("SettingsChanged")).toList();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getTopic()).isEqualTo("store-events");
        assertThat(events.getFirst().getPayload()).contains("\"settingKey\":\"orders.max-quote-revisions\"")
                .contains("\"settingValue\":\"3\"");

        AuditLogEntry audit = auditLogRepository.findAll().stream()
                .filter(e -> e.getAction().equals("SETTING_UPDATED")).reduce((a, b) -> b).orElseThrow();
        assertThat(audit.getActorRole()).isEqualTo("ROLE_SUPER_ADMIN");
        assertThat(audit.getBeforeState()).contains("2");
        assertThat(audit.getAfterState()).contains("3");

        // Internal view for other services
        mockMvc.perform(withBearer(get("/internal/settings"), serviceToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key == 'orders.max-quote-revisions')].value").value("3"));
    }

    @Test
    void invalidValuesAreRefusedWithoutSideEffects() throws Exception {
        String token = tokenFor(superAdmin());
        long history = jdbcTemplate.queryForObject("select count(*) from platform_settings_history", Long.class);
        String[][] cases = {
                {"orders.max-quote-revisions", "abc"},          // not a number
                {"orders.max-quote-revisions", "2.5"},          // not whole
                {"orders.max-quote-revisions", "21"},           // above range
                {"score.merchant.response-timeout", "2"},       // a penalty must not be positive
                {"timers.merchant-response-hours", "0"},        // below range
                {"fees.per-order-lkr", "10.123"},               // more than 2 decimals
                {"fees.per-order-lkr", "-1"},                   // negative money
                {"fees.enforced", "yes"},                       // not a boolean
                {"fees.enforced", "true"}};                     // enforcement does not exist (D11)
        for (String[] c : cases) {
            mockMvc.perform(json(withBearer(put("/api/super-admin/settings/" + c[0]), token),
                            "{\"value\":\"" + c[1] + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_SETTING_VALUE"));
        }
        mockMvc.perform(json(withBearer(put("/api/super-admin/settings/no.such.key"), token), "{\"value\":\"1\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UNKNOWN_SETTING"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from platform_settings_history", Long.class))
                .isEqualTo(history);
    }

    @Test
    void decimalsAreStoredWithTwoDecimals() throws Exception {
        mockMvc.perform(json(withBearer(put("/api/super-admin/settings/fees.per-order-lkr"), tokenFor(superAdmin())),
                        "{\"value\":\"12.5\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("12.50"));
    }

    @Test
    void readsAreCachedForTheTtlAndRefreshedAfterwards() {
        assertThat(settingsService.intValue(SettingKey.ORDERS_MAX_QUOTE_REVISIONS)).isEqualTo(2);
        // A change behind the service's back (another replica) is seen only once the 60 s cache expires
        jdbcTemplate.update("update platform_settings set value = '5' where setting_key = 'orders.max-quote-revisions'");
        assertThat(settingsService.intValue(SettingKey.ORDERS_MAX_QUOTE_REVISIONS)).isEqualTo(2);
        clock.advance(Duration.ofSeconds(61));
        assertThat(settingsService.intValue(SettingKey.ORDERS_MAX_QUOTE_REVISIONS)).isEqualTo(5);
    }
}
