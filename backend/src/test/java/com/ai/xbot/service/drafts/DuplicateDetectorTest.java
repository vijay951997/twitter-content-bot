package com.ai.xbot.service.drafts;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.xbot.service.ContentHasher;
import com.ai.xbot.service.drafts.DuplicateDetector.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DuplicateDetectorTest {

    private final ContentHasher hasher = new ContentHasher();

    private Existing existing(String text) {
        return new Existing(text, hasher.hash(text), Instant.parse("2026-11-01T00:00:00Z"));
    }

    @Test
    void identicalAfterNormalisationIsDuplicate() {
        String text = "Chennai needs better last-mile bus connectivity.";
        Verdict v = DuplicateDetector.check("  chennai needs better LAST-MILE bus connectivity. ",
                hasher.hash("  chennai needs better LAST-MILE bus connectivity. "), List.of(existing(text)));
        assertThat(v).isInstanceOf(Duplicate.class);
    }

    @Test
    void nearIdenticalIsDuplicate() {
        String a = "Should Chennai invest more in suburban rail before new flyovers are built across the city?";
        String b = "Should Chennai invest more in suburban rail before new flyovers are built across this city?";
        assertThat(DuplicateDetector.check(b, hasher.hash(b), List.of(existing(a)))).isInstanceOf(Duplicate.class);
    }

    @Test
    void moderatelySimilarIsWarning() {
        String a = "Should Chennai invest more in suburban rail before building new flyovers in the city?";
        String b = "Should Chennai invest more in suburban rail or bus fleets before new flyovers?";
        Verdict v = DuplicateDetector.check(b, hasher.hash(b), List.of(existing(a)));
        assertThat(v).isInstanceOf(Similar.class);
    }

    @Test
    void differentTopicIsUnique() {
        String a = "Government schools in Madurai report better attendance after breakfast scheme.";
        String b = "Coimbatore's new airport terminal timeline is still unclear.";
        assertThat(DuplicateDetector.check(b, hasher.hash(b), List.of(existing(a)))).isInstanceOf(Unique.class);
    }
}
