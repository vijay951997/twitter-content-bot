package com.ai.xbot.security;

import com.ai.xbot.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM encryption for OAuth tokens at rest. Output format: "v1:" + base64(12-byte IV || ciphertext+tag).
 * The key comes only from APP_TOKEN_ENCRYPTION_KEY; tokens and the key are never logged.
 */
@Component
public class TokenCipher {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    @org.springframework.beans.factory.annotation.Autowired
    public TokenCipher(AppProperties properties) {
        String b64 = properties.x() == null ? null : properties.x().tokenEncryptionKey();
        this.key = parse(b64);
    }

    TokenCipher(String base64Key) {
        this.key = parse(base64Key);
    }

    static SecretKeySpec parse(String b64) {
        if (b64 == null || b64.isBlank()) return null;
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(b64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("APP_TOKEN_ENCRYPTION_KEY must be base64");
        }
        if (raw.length != 32) throw new IllegalStateException("APP_TOKEN_ENCRYPTION_KEY must decode to 32 bytes (AES-256)");
        return new SecretKeySpec(raw, "AES");
    }

    public boolean available() { return key != null; }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        requireKey();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Token encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null) return null;
        requireKey();
        if (!stored.startsWith(PREFIX)) throw new IllegalStateException("Unknown token format");
        try {
            byte[] in = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
            return new String(c.doFinal(in, IV_BYTES, in.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Stored token can't be decrypted (was APP_TOKEN_ENCRYPTION_KEY changed?)");
        }
    }

    private void requireKey() {
        if (key == null) throw new IllegalStateException("APP_TOKEN_ENCRYPTION_KEY is not set");
    }
}
