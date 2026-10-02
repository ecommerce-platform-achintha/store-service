package com.achintha.storeservice.settings;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

/**
 * Every platform setting of section 7, with its type, default (exactly as the contract) and allowed range. The
 * defaults are also seeded by {@code V1__init.sql}; a test checks both agree.
 */
public enum SettingKey {
    TIMERS_MERCHANT_RESPONSE_HOURS("timers.merchant-response-hours", SettingValueType.INTEGER, "24", 1, 720),
    TIMERS_CUSTOMER_CONFIRMATION_HOURS("timers.customer-confirmation-hours", SettingValueType.INTEGER, "24", 1, 720),
    TIMERS_PAYMENT_SUBMISSION_HOURS("timers.payment-submission-hours", SettingValueType.INTEGER, "24", 1, 720),
    TIMERS_PAYMENT_VERIFICATION_HOURS("timers.payment-verification-hours", SettingValueType.INTEGER, "24", 1, 720),
    TIMERS_SHIP_BY_HOURS("timers.ship-by-hours", SettingValueType.INTEGER, "72", 1, 720),
    TIMERS_AUTO_COMPLETE_DAYS("timers.auto-complete-days", SettingValueType.INTEGER, "7", 1, 60),
    TIMERS_BAN_GRACE_DAYS("timers.ban-grace-days", SettingValueType.INTEGER, "14", 1, 90),
    SCORE_CUSTOMER_COMPLETED("score.customer.completed", SettingValueType.INTEGER, "1", 0, 100),
    SCORE_CUSTOMER_DECLINED("score.customer.declined", SettingValueType.INTEGER, "-1", -100, 0),
    SCORE_CUSTOMER_EXPIRED("score.customer.expired", SettingValueType.INTEGER, "-1", -100, 0),
    COD_OBJECTION_WINDOW_DAYS("cod.objection-window-days", SettingValueType.INTEGER, "7", 1, 60),
    SCORE_CUSTOMER_COD_REFUSAL_LIMIT("score.customer.cod-refusal-limit", SettingValueType.INTEGER, "3", 1, 20),
    SCORE_CUSTOMER_COD_SUSPENSION_MONTHS("score.customer.cod-suspension-months", SettingValueType.INTEGER, "3", 1,
            36),
    SCORE_MERCHANT_COMPLETED("score.merchant.completed", SettingValueType.INTEGER, "1", 0, 100),
    SCORE_MERCHANT_RESPONSE_TIMEOUT("score.merchant.response-timeout", SettingValueType.INTEGER, "-2", -100, 0),
    SCORE_MERCHANT_LATE_SHIPMENT("score.merchant.late-shipment", SettingValueType.INTEGER, "-1", -100, 0),
    SCORE_MERCHANT_LATE_VERIFICATION("score.merchant.late-verification", SettingValueType.INTEGER, "-1", -100, 0),
    SCORE_MERCHANT_COMPLAINT_UPHELD("score.merchant.complaint-upheld", SettingValueType.INTEGER, "-3", -100, 0),
    SCORE_MERCHANT_AT_RISK_THRESHOLD("score.merchant.at-risk-threshold", SettingValueType.INTEGER, "-5", -10000,
            10000),
    ORDERS_MAX_OPEN_UNCONFIRMED_PER_CUSTOMER("orders.max-open-unconfirmed-per-customer", SettingValueType.INTEGER,
            "10", 1, 1000),
    ORDERS_MAX_QUOTE_REVISIONS("orders.max-quote-revisions", SettingValueType.INTEGER, "2", 0, 20),
    COMPLAINTS_WINDOW_DAYS("complaints.window-days", SettingValueType.INTEGER, "30", 1, 365),
    MERCHANT_MAX_APPLICATION_ATTEMPTS("merchant.max-application-attempts", SettingValueType.INTEGER, "3", 1, 20),
    AUTH_ACCESS_TOKEN_MINUTES("auth.access-token-minutes", SettingValueType.INTEGER, "10", 1, 60),
    AUTH_REFRESH_DAYS("auth.refresh-days", SettingValueType.INTEGER, "14", 1, 90),
    AUTH_MAX_FAILED_LOGINS("auth.max-failed-logins", SettingValueType.INTEGER, "5", 1, 50),
    AUTH_LOCKOUT_MINUTES("auth.lockout-minutes", SettingValueType.INTEGER, "15", 1, 1440),
    FEES_MONTHLY_LKR("fees.monthly-lkr", SettingValueType.DECIMAL, "500.00", 0, 1_000_000),
    FEES_PER_ORDER_LKR("fees.per-order-lkr", SettingValueType.DECIMAL, "10.00", 0, 100_000),
    /** D11: fees are recorded, never enforced. Only {@code false} is accepted until enforcement exists. */
    FEES_ENFORCED("fees.enforced", SettingValueType.BOOLEAN, "false", 0, 0);

    private final String key;
    private final SettingValueType type;
    private final String defaultValue;
    private final long min;
    private final long max;

    SettingKey(String key, SettingValueType type, String defaultValue, long min, long max) {
        this.key = key;
        this.type = type;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
    }

    public String key() {
        return key;
    }

    public SettingValueType type() {
        return type;
    }

    public String defaultValue() {
        return defaultValue;
    }

    /** Inclusive bounds for INTEGER and DECIMAL settings; {@code null} for BOOLEAN. */
    public BigDecimal min() {
        return type == SettingValueType.BOOLEAN ? null : BigDecimal.valueOf(min);
    }

    public BigDecimal max() {
        return type == SettingValueType.BOOLEAN ? null : BigDecimal.valueOf(max);
    }

    public static Optional<SettingKey> fromKey(String key) {
        return Arrays.stream(values()).filter(k -> k.key.equals(key)).findFirst();
    }
}
