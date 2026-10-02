package com.achintha.storeservice.score;

import com.achintha.storeservice.settings.SettingKey;

/** Merchant score changes (section 7), each with the setting that holds its delta. */
public enum ScoreEventType {
    ORDER_COMPLETED(SettingKey.SCORE_MERCHANT_COMPLETED),
    RESPONSE_TIMEOUT(SettingKey.SCORE_MERCHANT_RESPONSE_TIMEOUT),
    LATE_SHIPMENT(SettingKey.SCORE_MERCHANT_LATE_SHIPMENT),
    LATE_VERIFICATION(SettingKey.SCORE_MERCHANT_LATE_VERIFICATION),
    COMPLAINT_UPHELD(SettingKey.SCORE_MERCHANT_COMPLAINT_UPHELD);

    private final SettingKey deltaSetting;

    ScoreEventType(SettingKey deltaSetting) {
        this.deltaSetting = deltaSetting;
    }

    public SettingKey deltaSetting() {
        return deltaSetting;
    }
}
