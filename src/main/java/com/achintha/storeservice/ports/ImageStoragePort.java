package com.achintha.storeservice.ports;

/**
 * Stores images (store logos, review attachments) by opaque key (section 10). Clients upload elsewhere and send the
 * keys; this service only checks and resolves them.
 */
public interface ImageStoragePort {

    /** Whether an uploaded image exists under this key. */
    boolean exists(String key);

    /** A URL the client can display. */
    String urlFor(String key);
}
