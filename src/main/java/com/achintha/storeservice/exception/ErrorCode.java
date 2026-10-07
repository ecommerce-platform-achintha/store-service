package com.achintha.storeservice.exception;

/** Stable, machine-readable error codes returned in {@link ApiError#code()}. Never rename a published code. */
public enum ErrorCode {
    // Generic
    VALIDATION_FAILED,
    MALFORMED_REQUEST,
    BAD_REQUEST,
    INVALID_SORT,
    NOT_FOUND,
    METHOD_NOT_ALLOWED,
    UNSUPPORTED_MEDIA_TYPE,
    CONCURRENT_MODIFICATION,
    INTERNAL_ERROR,
    SERVICE_UNAVAILABLE,

    // Authentication and authorization
    UNAUTHORIZED,
    ACCESS_DENIED,
    TOKEN_REVOKED,
    ACCOUNT_BANNED,
    PASSWORD_CHANGE_REQUIRED,

    // Stores
    STORE_NAME_TAKEN,
    STORE_PROFILE_REQUIRED,

    // Master data, bank accounts, couriers, shipping
    BANK_CODE_TAKEN,
    BANK_NOT_AVAILABLE,
    BANK_IN_USE,
    BANK_ACCOUNT_LIMIT_REACHED,
    COURIER_CODE_TAKEN,
    COURIER_NOT_AVAILABLE,
    COURIER_IN_USE,
    COURIER_NOT_ENABLED,
    SHIPPING_TEMPLATE_EXISTS,
    SHIPPING_ESTIMATE_UNAVAILABLE,

    // Settings and holidays
    UNKNOWN_SETTING,
    INVALID_SETTING_VALUE,
    HOLIDAY_DATE_TAKEN,

    // Reviews
    REVIEW_ALREADY_EXISTS,
    REPLY_ALREADY_EXISTS
}
