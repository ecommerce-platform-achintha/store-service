package com.achintha.storeservice.common;

/** Shared Bean Validation patterns, so every DTO validates the same fields the same way. */
public final class Validation {

    /** E.164-ish: optional '+', no leading zero after it, 8 to 15 digits (e.g. +94771234567). Stored without spaces. */
    public static final String PHONE_REGEX = "^\\+?[1-9]\\d{7,14}$";
    public static final String PHONE_MESSAGE = "must be a phone number in international format, e.g. +94771234567";
    /** Opaque keys of the mock image storage. */
    public static final String STORAGE_KEY_REGEX = "^[A-Za-z0-9][A-Za-z0-9._/-]{0,199}$";
    /** Master-data codes chosen by the super admin: BOC, COMB, DOMEX, KOOMBIYO. */
    public static final String CODE_REGEX = "^[A-Z][A-Z0-9_]{1,19}$";
    public static final String CODE_MESSAGE = "must be 2-20 characters: upper-case letters, digits or '_', starting "
            + "with a letter";
    /** Bank account numbers: digits only (spaces and dashes are removed before validation). */
    public static final String ACCOUNT_NUMBER_REGEX = "^\\d{6,20}$";
    public static final String PUBLIC_ID_REGEX = "^[A-Z]{3}-\\d{4}-[0-9A-HJKMNP-TV-Z]{6}$";
    public static final int REASON_MAX = 500;
    public static final int TEXT_MAX = 2000;

    private Validation() {
    }
}
