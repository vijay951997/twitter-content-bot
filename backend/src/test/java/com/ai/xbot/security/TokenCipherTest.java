package com.ai.xbot.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class TokenCipherTest {

    static String key() {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    @Test
    void roundTripsAndNeverStoresPlaintext() {
        TokenCipher c = new TokenCipher(key());
        String enc = c.encrypt("secret-access-token");
        assertThat(enc).startsWith("v1:").doesNotContain("secret-access-token");
        assertThat(c.decrypt(enc)).isEqualTo("secret-access-token");
        assertThat(c.encrypt("same")).isNotEqualTo(c.encrypt("same")); // random IV
    }

    @Test
    void tamperedCiphertextIsRejected() {
        TokenCipher c = new TokenCipher(key());
        String enc = c.encrypt("token");
        byte[] raw = Base64.getDecoder().decode(enc.substring(3));
        raw[raw.length - 1] ^= 1;
        assertThatThrownBy(() -> c.decrypt("v1:" + Base64.getEncoder().encodeToString(raw)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void wrongKeyCannotDecrypt() {
        String enc = new TokenCipher(key()).encrypt("token");
        assertThatThrownBy(() -> new TokenCipher(key()).decrypt(enc)).hasMessageContaining("APP_TOKEN_ENCRYPTION_KEY");
    }

    @Test
    void keyMustBe32Bytes() {
        assertThatThrownBy(() -> new TokenCipher(Base64.getEncoder().encodeToString(new byte[16])))
                .hasMessageContaining("32 bytes");
        assertThat(new TokenCipher((String) null).available()).isFalse();
    }
}
