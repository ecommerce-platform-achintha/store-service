package com.achintha.storeservice.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.achintha.storeservice.crypto.AccountNumberCipher;
import com.achintha.storeservice.crypto.CryptoProperties;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AccountNumberCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private final AccountNumberCipher cipher = new AccountNumberCipher(new CryptoProperties(KEY));

    @Test
    void roundTripsWithAFreshIvEveryTime() {
        String first = cipher.encrypt("1234567890");
        String second = cipher.encrypt("1234567890");
        assertThat(first).isNotEqualTo(second).startsWith("v1:");
        assertThat(cipher.decrypt(first)).isEqualTo("1234567890");
        assertThat(cipher.decrypt(second)).isEqualTo("1234567890");
    }

    @Test
    void tamperingIsDetected() {
        String stored = cipher.encrypt("1234567890");
        byte[] raw = Base64.getDecoder().decode(stored.substring(3));
        raw[raw.length - 1] ^= 1;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(raw);
        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("1234567890");
    }

    @Test
    void anotherKeyCannotDecrypt() {
        byte[] other = new byte[32];
        other[0] = 1;
        AccountNumberCipher otherCipher = new AccountNumberCipher(
                new CryptoProperties(Base64.getEncoder().encodeToString(other)));
        assertThatThrownBy(() -> otherCipher.decrypt(cipher.encrypt("1234567890")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesMissingOrWeakKeys() {
        assertThatThrownBy(() -> new AccountNumberCipher(new CryptoProperties("")))
                .hasMessageContaining("ACCOUNT_NUMBER_ENCRYPTION_KEY");
        assertThatThrownBy(() -> new AccountNumberCipher(new CryptoProperties(
                Base64.getEncoder().encodeToString(new byte[16])))).hasMessageContaining("32 bytes");
    }

    @Test
    void masksToTheLastFourDigits() {
        assertThat(AccountNumberCipher.mask(AccountNumberCipher.lastFour("0081234567890"))).isEqualTo("****7890");
    }
}
