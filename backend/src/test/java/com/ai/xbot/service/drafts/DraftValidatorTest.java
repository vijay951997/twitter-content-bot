package com.ai.xbot.service.drafts;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.xbot.entity.enums.ContentLanguage;
import com.ai.xbot.entity.enums.PostSlot;
import com.ai.xbot.entity.enums.VerificationStatus;
import com.ai.xbot.service.ai.AiGenerationResult.AiDraft;
import com.ai.xbot.service.drafts.DraftValidator.Accepted;
import com.ai.xbot.service.drafts.DraftValidator.Rejected;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DraftValidatorTest {

    private static final String SUPPLIED = "https://www.tn.gov.in/press/123";

    private static AiDraft draft(String content, List<String> urls, String status, List<String> verified, List<String> flags) {
        return new AiDraft(content, "en", "Public Transport", urls, verified, List.of(), status, flags, 0.9);
    }

    private static DraftValidator.Outcome check(AiDraft d, boolean hasNotes) {
        return DraftValidator.validate(d, ContentLanguage.ENGLISH, PostSlot.MORNING, List.of(SUPPLIED), hasNotes);
    }

    @Test
    @DisplayName("A clean draft is accepted with sanitised category")
    void accepts() {
        var out = check(draft("New bus routes announced for Chennai suburbs.", List.of(SUPPLIED), "PARTIALLY_VERIFIED",
                List.of("routes announced"), List.of()), true);
        assertThat(out).isInstanceOf(Accepted.class);
        Accepted a = (Accepted) out;
        assertThat(a.category()).isEqualTo("PUBLIC_TRANSPORT");
        assertThat(a.sourceUrls()).containsExactly(SUPPLIED);
        assertThat(a.verificationStatus()).isEqualTo(VerificationStatus.PARTIALLY_VERIFIED);
    }

    @Test
    @DisplayName("AI-reported VERIFIED is capped: it is not independent verification")
    void verifiedIsCapped() {
        Accepted a = (Accepted) check(draft("Bus routes announced.", List.of(SUPPLIED), "VERIFIED", List.of("x"), List.of()), true);
        assertThat(a.verificationStatus()).isEqualTo(VerificationStatus.PARTIALLY_VERIFIED);
    }

    @Test
    @DisplayName("Without source material everything is UNVERIFIED and 'verified' claims are moved")
    void noNotesMeansUnverified() {
        Accepted a = (Accepted) check(draft("What should Chennai prioritise in public transport?", List.of(), "VERIFIED",
                List.of("a claim"), List.of()), false);
        assertThat(a.verificationStatus()).isEqualTo(VerificationStatus.UNVERIFIED);
        assertThat(a.verifiedClaims()).isEmpty();
        assertThat(a.unverifiedClaims()).containsExactly("a claim");
        assertThat(a.riskFlags()).contains(DraftValidator.FLAG_HAS_UNVERIFIED_CLAIMS);
    }

    @Test
    @DisplayName("Invented source URLs are dropped and flagged")
    void inventedUrlDropped() {
        Accepted a = (Accepted) check(draft("Bus routes announced.", List.of(SUPPLIED, "https://fake-news.example/x"),
                "PARTIALLY_VERIFIED", List.of(), List.of()), true);
        assertThat(a.sourceUrls()).containsExactly(SUPPLIED);
        assertThat(a.riskFlags()).contains(DraftValidator.FLAG_AI_URL_REMOVED);
    }

    @Test
    @DisplayName("A draft whose text links somewhere not supplied is discarded")
    void unsuppliedLinkInTextRejected() {
        var out = check(draft("Read more https://made-up.example/story", List.of(), "UNVERIFIED", List.of(), List.of()), false);
        assertThat(out).isInstanceOf(Rejected.class);
        assertThat(((Rejected) out).reason()).contains("not supplied");
    }

    @Test
    @DisplayName("The supplied link may appear in the text")
    void suppliedLinkInTextAllowed() {
        var out = check(draft("Details: " + SUPPLIED, List.of(SUPPLIED), "PARTIALLY_VERIFIED", List.of(), List.of()), true);
        assertThat(out).isInstanceOf(Accepted.class);
    }

    @Test
    void tooLongRejected() {
        var out = check(draft("word ".repeat(70), List.of(), "UNVERIFIED", List.of(), List.of()), false);
        assertThat(((Rejected) out).reason()).contains("Too long");
    }

    @Test
    void wrongLanguageRejected() {
        var out = check(draft("சென்னை மெட்ரோ பணிகள் எப்போது முடியும்?", List.of(), "UNVERIFIED", List.of(), List.of()), false);
        assertThat(((Rejected) out).reason()).contains("language");
    }

    @Test
    void emptyRejected() {
        assertThat(check(draft("   ", List.of(), "UNVERIFIED", List.of(), List.of()), false)).isInstanceOf(Rejected.class);
    }

    @Test
    void unknownRiskFlagsIgnoredKnownKept() {
        Accepted a = (Accepted) check(draft("A neutral civic question?", List.of(), "UNVERIFIED", List.of(),
                List.of("INFLAMMATORY", "MADE_UP_FLAG")), false);
        assertThat(a.riskFlags()).contains("INFLAMMATORY").doesNotContain("MADE_UP_FLAG");
    }

    @Test
    void qualityIsClamped() {
        assertThat(DraftValidator.quality(1.7)).isEqualByComparingTo("1.000");
        assertThat(DraftValidator.quality(-2.0)).isEqualByComparingTo("0.000");
        assertThat(DraftValidator.quality(null)).isNull();
    }

    @Test
    void emptyCategoryFallsBackToSlotDefault() {
        assertThat(DraftValidator.category(" ", PostSlot.EVENING)).isEqualTo("PUBLIC_INTEREST");
    }
}
