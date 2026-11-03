package com.ai.xbot.service.text;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Word-set Jaccard similarity for near-duplicate detection. Unicode-aware, so Tamil words are
 * tokenised correctly (combining vowel signs stay attached to their consonants).
 * URLs, mentions and hashtags are ignored so the same text with a different link still matches.
 */
public final class TextSimilarity {

    private TextSimilarity() {}

    public static Set<String> tokens(String text) {
        if (text == null) return Set.of();
        String cleaned = Normalizer.normalize(text, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("https?://\\S+", " ")
                .replaceAll("[@#]", " ");
        return Arrays.stream(cleaned.split("[^\\p{L}\\p{M}\\p{N}]+"))
                .filter(t -> t.codePointCount(0, t.length()) > 1)
                .collect(Collectors.toCollection(HashSet::new));
    }

    public static double jaccard(String a, String b) {
        Set<String> ta = tokens(a);
        Set<String> tb = tokens(b);
        if (ta.isEmpty() && tb.isEmpty()) return 1.0;
        if (ta.isEmpty() || tb.isEmpty()) return 0.0;
        Set<String> inter = new HashSet<>(ta);
        inter.retainAll(tb);
        Set<String> union = new HashSet<>(ta);
        union.addAll(tb);
        return (double) inter.size() / union.size();
    }
}
