package com.achintha.storeservice.shipping;

/** How a shipping template prices a parcel (an estimate only; the merchant enters the real charge in the quote). */
public enum ShippingRuleType {
    /** One amount for every parcel. */
    FLAT,
    /** An amount per destination district; {@code baseAmount} (optional) for the other districts. */
    PER_DISTRICT,
    /** Weight bands "up to N kg"; {@code baseAmount} (optional) above the heaviest band. */
    WEIGHT_BAND
}
