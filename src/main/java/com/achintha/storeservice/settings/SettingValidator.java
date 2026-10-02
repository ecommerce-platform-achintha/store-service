package com.achintha.storeservice.settings;

import com.achintha.storeservice.exception.ApiException;
import com.achintha.storeservice.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.Locale;

/** Checks a new value against the setting's type and range and returns its canonical stored form. */
public final class SettingValidator {

    private SettingValidator() {
    }

    public static String normalize(SettingKey key, String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid(key, "a value is required");
        }
        String value = raw.strip();
        return switch (key.type()) {
            case INTEGER -> {
                BigDecimal number = parse(key, value);
                if (number.stripTrailingZeros().scale() > 0) {
                    throw invalid(key, "must be a whole number");
                }
                checkRange(key, number);
                yield number.toBigIntegerExact().toString();
            }
            case DECIMAL -> {
                BigDecimal number = parse(key, value);
                if (number.stripTrailingZeros().scale() > 2) {
                    throw invalid(key, "must have at most 2 decimals");
                }
                checkRange(key, number);
                yield number.setScale(2).toPlainString();
            }
            case BOOLEAN -> {
                String bool = value.toLowerCase(Locale.ROOT);
                if (!bool.equals("true") && !bool.equals("false")) {
                    throw invalid(key, "must be true or false");
                }
                if (key == SettingKey.FEES_ENFORCED && bool.equals("true")) {
                    throw invalid(key, "fee enforcement is not available (fees are recorded only)");
                }
                yield bool;
            }
        };
    }

    private static BigDecimal parse(SettingKey key, String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw invalid(key, "must be a number");
        }
    }

    private static void checkRange(SettingKey key, BigDecimal number) {
        if (number.compareTo(key.min()) < 0 || number.compareTo(key.max()) > 0) {
            throw invalid(key, "must be between " + key.min().toPlainString() + " and " + key.max().toPlainString());
        }
    }

    private static ApiException invalid(SettingKey key, String problem) {
        return ApiException.badRequest(ErrorCode.INVALID_SETTING_VALUE, key.key() + " " + problem);
    }
}
