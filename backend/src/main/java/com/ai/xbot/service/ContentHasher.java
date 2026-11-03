package com.ai.xbot.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Normalises post text and hashes it with SHA-256.
 * Used to bind an approval to exact content (any edit changes the hash) and,
 * from Phase 2 on, as the first stage of duplicate detection.
 * Normalisation is Unicode-safe for Tamil script (NFC, no ASCII-only stripping).
 */
@Component
public class ContentHasher {

    public String normalize(String text) {
        if (text == null) return "";
        String nfc = Normalizer.normalize(text, Normalizer.Form.NFC);
        return nfc.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }

    public String hash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(normalize(text).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
