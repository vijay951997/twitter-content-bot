package com.ai.xbot.service.text;

import com.ai.xbot.entity.enums.ContentLanguage;

/**
 * Script-based language check used to confirm the AI wrote in the language that was asked for.
 * <ul>
 *   <li>ta: mostly Tamil script (≥ 60% of letters)</li>
 *   <li>en: no Tamil script and some Latin letters</li>
 *   <li>mixed (Tanglish): Latin letters present and Tamil script below 60% — covers Tamil written
 *       in Roman letters as well as Tamil script mixed with English words</li>
 * </ul>
 * This checks script, not grammar; reviewing natural phrasing stays a human job.
 */
public final class LanguageDetector {

    static final double TAMIL_MAJORITY = 0.6;

    private LanguageDetector() {}

    public record ScriptStats(int tamilLetters, int latinLetters) {
        public int letters() { return tamilLetters + latinLetters; }
        public double tamilRatio() { return letters() == 0 ? 0 : (double) tamilLetters / letters(); }
    }

    public static ScriptStats stats(String text) {
        int tamil = 0, latin = 0;
        String withoutUrls = text == null ? "" : text.replaceAll("https?://\\S+", " ")
                .replaceAll("[#@]\\w+", " ");
        for (int cp : withoutUrls.codePoints().toArray()) {
            if (cp >= 0x0B80 && cp <= 0x0BFF) {
                if (Character.isLetter(cp) || Character.getType(cp) == Character.NON_SPACING_MARK
                        || Character.getType(cp) == Character.COMBINING_SPACING_MARK) tamil++;
            } else if (Character.isLetter(cp) && Character.UnicodeScript.of(cp) == Character.UnicodeScript.LATIN) {
                latin++;
            }
        }
        return new ScriptStats(tamil, latin);
    }

    public static boolean matches(String text, ContentLanguage expected) {
        ScriptStats s = stats(text);
        if (s.letters() == 0) return false;
        return switch (expected) {
            case TAMIL -> s.tamilRatio() >= TAMIL_MAJORITY;
            case ENGLISH -> s.tamilLetters() == 0;
            case MIXED -> s.latinLetters() > 0 && s.tamilRatio() < TAMIL_MAJORITY;
        };
    }
}
