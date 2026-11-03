package com.ai.xbot.service.text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * X ("weighted") post length, following the published twitter-text v3 configuration:
 * code points in the ranges below weigh 1, everything else weighs 2, every URL counts as 23,
 * and the limit is 280. Tamil (U+0B80–U+0BFF) falls in the first range, so it weighs 1.
 * Emoji are counted per code point here (slightly conservative versus X, never under-counting).
 */
public final class XTextLength {

    public static final int MAX_WEIGHTED_LENGTH = 280;
    static final int URL_WEIGHT = 23;

    private static final Pattern URL = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private static final int[][] LIGHT_RANGES = {
            {0, 4351}, {8192, 8205}, {8208, 8223}, {8242, 8247}
    };

    private XTextLength() {}

    public static int weightedLength(String text) {
        if (text == null || text.isEmpty()) return 0;
        String nfc = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC);
        int total = 0;
        int last = 0;
        Matcher m = URL.matcher(nfc);
        while (m.find()) {
            total += weightOf(nfc.substring(last, m.start()));
            total += URL_WEIGHT;
            last = m.end();
        }
        total += weightOf(nfc.substring(last));
        return total;
    }

    public static boolean fits(String text) {
        return weightedLength(text) <= MAX_WEIGHTED_LENGTH;
    }

    private static int weightOf(String s) {
        return s.codePoints().map(XTextLength::codePointWeight).sum();
    }

    private static int codePointWeight(int cp) {
        for (int[] r : LIGHT_RANGES) {
            if (cp >= r[0] && cp <= r[1]) return 1;
        }
        return 2;
    }
}
