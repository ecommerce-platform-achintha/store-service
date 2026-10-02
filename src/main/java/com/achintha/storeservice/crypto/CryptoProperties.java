package com.achintha.storeservice.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param accountNumberKey base64 of a 32-byte AES key (secret: from the environment or a secret store, never
 *                         committed). Validated by {@link AccountNumberCipher} at startup.
 */
@ConfigurationProperties("app.crypto")
public record CryptoProperties(String accountNumberKey) {

    @Override
    public String toString() {
        return "CryptoProperties[accountNumberKey=****]";
    }
}
