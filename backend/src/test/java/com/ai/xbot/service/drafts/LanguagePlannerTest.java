package com.ai.xbot.service.drafts;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.xbot.entity.enums.ContentLanguage;
import com.ai.xbot.entity.enums.PostSlot;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LanguagePlannerTest {

    private static Map<ContentLanguage, Integer> countOver(int days, Map<String, Integer> mix) {
        Map<ContentLanguage, Integer> counts = new EnumMap<>(ContentLanguage.class);
        LocalDate start = LocalDate.of(2026, 11, 1);
        for (int d = 0; d < days; d++) {
            for (PostSlot s : PostSlot.values()) {
                counts.merge(LanguagePlanner.languageFor(start.plusDays(d), s, mix), 1, Integer::sum);
            }
        }
        return counts;
    }

    @Test
    void fiftyFiftyMixIsBalancedOverAMonth() {
        Map<ContentLanguage, Integer> c = countOver(30, Map.of("ta", 50, "en", 50));
        assertThat(c.get(ContentLanguage.TAMIL)).isBetween(42, 48);
        assertThat(c.get(ContentLanguage.ENGLISH)).isBetween(42, 48);
    }

    @Test
    void threeWayMixFollowsShares() {
        Map<String, Integer> mix = new LinkedHashMap<>();
        mix.put("ta", 40); mix.put("en", 40); mix.put("mixed", 20);
        Map<ContentLanguage, Integer> c = countOver(100, mix); // 300 slots
        assertThat(c.get(ContentLanguage.MIXED)).isBetween(55, 65);
        assertThat(c.get(ContentLanguage.TAMIL)).isBetween(114, 126);
    }

    @Test
    void zeroShareLanguageIsNeverPicked() {
        Map<ContentLanguage, Integer> c = countOver(30, Map.of("ta", 100, "en", 0));
        assertThat(c).containsOnlyKeys(ContentLanguage.TAMIL);
    }

    @Test
    void deterministicForSameDateAndSlot() {
        LocalDate d = LocalDate.of(2026, 11, 4);
        Map<String, Integer> mix = Map.of("ta", 50, "en", 50);
        assertThat(LanguagePlanner.languageFor(d, PostSlot.EVENING, mix))
                .isEqualTo(LanguagePlanner.languageFor(d, PostSlot.EVENING, mix));
    }
}
