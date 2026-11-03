package com.ai.xbot.entity.enums;

import java.util.Arrays;

/** Language of a post. Stored as its code (ta / en / mixed). */
public enum ContentLanguage {
    TAMIL("ta"), ENGLISH("en"), MIXED("mixed");

    private final String code;

    ContentLanguage(String code) { this.code = code; }

    public String code() { return code; }

    public static ContentLanguage fromCode(String code) {
        return Arrays.stream(values())
                .filter(l -> l.code.equalsIgnoreCase(code == null ? "" : code.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported language code: " + code));
    }
}
