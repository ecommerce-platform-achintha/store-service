package com.achintha.storeservice.fee;

public enum LedgerEntryType {
    /** {@code fees.per-order-lkr}, recorded on {@code OrderCompleted}. */
    ORDER_FEE,
    /** {@code fees.monthly-lkr}; reserved, nothing bills it yet (D11). */
    MONTHLY_FEE,
    /** A super-admin credit (negative amount). */
    CREDIT
}
