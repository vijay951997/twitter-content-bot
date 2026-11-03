package com.ai.xbot.service.drafts;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.xbot.entity.enums.ContentLanguage;
import com.ai.xbot.entity.enums.PostSlot;
import com.ai.xbot.entity.enums.VerificationStatus;
import com.ai.xbot.news.RelevanceFilter;
import com.ai.xbot.service.ai.AiGenerationResult.AiDraft;
import com.ai.xbot.service.text.LanguageDetector;
import java.util.List;
import org.junit.jupiter.api.Test;

class FocusAndHashtagTest {

    static AiDraft draft(String text) {
        return new AiDraft(text, "en", "POLICY", List.of(), List.of(), List.of(), "UNVERIFIED", List.of(), 0.8);
    }

    @Test
    void countsHashtagsInAnyScript() {
        assertThat(DraftValidator.hashtagCount("New scheme launched #TVK #CMVijay #தவெக")).isEqualTo(3);
        assertThat(DraftValidator.hashtagCount("No tags, just C# code and a#b")).isZero();
    }

    @Test
    void rejectsTooManyHashtags() {
        var out = DraftValidator.validate(draft("Proud of this step. #TVK #CMVijay #Vijay #TamilNadu"),
                ContentLanguage.ENGLISH, PostSlot.MORNING, List.of(), VerificationStatus.UNVERIFIED, 3);
        assertThat(out).isInstanceOf(DraftValidator.Rejected.class);
        assertThat(((DraftValidator.Rejected) out).reason()).contains("hashtags");
    }

    @Test
    void englishPostWithTamilHashtagIsStillEnglish() {
        assertThat(LanguageDetector.matches("What should be the next priority for Chennai? #TVK #தவெக",
                ContentLanguage.ENGLISH)).isTrue();
        var out = DraftValidator.validate(draft("What should be the next priority for Chennai? #TVK #தவெக"),
                ContentLanguage.ENGLISH, PostSlot.MORNING, List.of(), VerificationStatus.UNVERIFIED, 3);
        assertThat(out).isInstanceOf(DraftValidator.Accepted.class);
    }

    @Test
    void focusKeywordsMatchEnglishAndTamil() {
        List<String> focus = List.of("tvk", "vijay", "தவெக", "முதல்வர்");
        assertThat(RelevanceFilter.matchesFocus("CM Vijay inaugurates new hospital block", "", focus)).isTrue();
        assertThat(RelevanceFilter.matchesFocus("முதல்வர் புதிய திட்டத்தை தொடங்கினார்", "", focus)).isTrue();
        assertThat(RelevanceFilter.matchesFocus("Corporation clears drains in Velachery", "", focus)).isFalse();
        assertThat(RelevanceFilter.matchesFocus("Stvkx company results", "", focus)).isFalse(); // whole word
    }
}
