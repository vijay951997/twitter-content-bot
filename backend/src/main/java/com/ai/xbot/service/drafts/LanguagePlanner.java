package com.ai.xbot.service.drafts;

import com.ai.xbot.entity.enums.ContentLanguage;
import com.ai.xbot.entity.enums.PostSlot;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Turns the configured language mix (e.g. ta:50,en:50) into a language for each daily slot.
 *
 * Each slot gets an index (day × 3 + slot). The index is mapped to a point in [0, 1) using the
 * golden-ratio sequence, which spreads points evenly; the point falls into one language's share
 * of the mix. Over any run of days the proportions closely match the mix, results are
 * deterministic for a given date and slot, and no history is needed.
 */
public final class LanguagePlanner {

    private static final double GOLDEN = 0.6180339887498949;

    private LanguagePlanner() {}

    public static ContentLanguage languageFor(LocalDate date, PostSlot slot, Map<String, Integer> mix) {
        if (mix == null || mix.isEmpty()) throw new IllegalArgumentException("Language mix is empty");
        int total = mix.values().stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) throw new IllegalArgumentException("Language mix total must be positive");

        List<String> codes = new ArrayList<>(mix.keySet());
        Collections.sort(codes); // stable regardless of map ordering

        long index = date.toEpochDay() * PostSlot.values().length + slot.ordinal();
        double point = (index * GOLDEN) % 1.0;

        double cumulative = 0;
        for (String code : codes) {
            cumulative += (double) mix.get(code) / total;
            if (point < cumulative) return ContentLanguage.fromCode(code);
        }
        return ContentLanguage.fromCode(codes.get(codes.size() - 1));
    }
}
