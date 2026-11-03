package com.ai.xbot.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import org.junit.jupiter.api.Test;

class ContentHasherTest {

    private final ContentHasher hasher = new ContentHasher();

    @Test
    void whitespaceAndCaseDoNotChangeHash() {
        assertThat(hasher.hash("  Metro   Rail\nPhase 2 update "))
                .isEqualTo(hasher.hash("metro rail phase 2 update"));
    }

    @Test
    void anyRealEditChangesHash() {
        assertThat(hasher.hash("Budget allocated for schools"))
                .isNotEqualTo(hasher.hash("Budget allocated for hospitals"));
    }

    @Test
    void tamilTextIsPreservedAndNormalised() {
        String tamil = "சென்னை மெட்ரோ";
        String decomposed = Normalizer.normalize(tamil, Normalizer.Form.NFD);
        assertThat(hasher.normalize(tamil)).isEqualTo("சென்னை மெட்ரோ");
        assertThat(hasher.hash(decomposed)).isEqualTo(hasher.hash(tamil));
    }

    @Test
    void hashIsSha256Hex() {
        assertThat(hasher.hash("x")).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void nullIsTreatedAsEmpty() {
        assertThat(hasher.hash(null)).isEqualTo(hasher.hash(""));
    }
}
