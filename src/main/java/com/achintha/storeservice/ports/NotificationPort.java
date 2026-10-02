package com.achintha.storeservice.ports;

/** Sends SMS / email / in-app notifications (section 10). Implementations must never log message secrets. */
public interface NotificationPort {

    /** Alerts the admin team (in-app record), e.g. a merchant whose score fell below the at-risk threshold. */
    void notifyAdmins(String template, String subject);
}
