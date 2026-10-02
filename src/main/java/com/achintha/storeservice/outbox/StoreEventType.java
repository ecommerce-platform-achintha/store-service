package com.achintha.storeservice.outbox;

/** Events on the {@code store-events} topic (section 9). The enum name is the wire {@code eventType}. */
public enum StoreEventType {
    /** A store's {@code published} / {@code acceptingOrders} changed (profile completed, ban, unban, ...). */
    StoreVisibilityChanged,
    /** A platform setting was updated: drop cached settings. */
    SettingsChanged,
    /** A holiday was added, changed or removed: drop cached holidays (deadline calculation). */
    HolidaysChanged
}
