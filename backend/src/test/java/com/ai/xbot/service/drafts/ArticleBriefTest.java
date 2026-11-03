package com.ai.xbot.service.drafts;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.xbot.entity.FactualClaim;
import com.ai.xbot.entity.NewsArticle;
import com.ai.xbot.entity.NewsSource;
import com.ai.xbot.entity.enums.*;
import com.ai.xbot.service.ai.AiGenerationResult.AiDraft;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArticleBriefTest {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    final NewsSource source = new NewsSource("The Hindu – Tamil Nadu", "https://www.thehindu.com/tn/", "https://www.thehindu.com/tn.rss",
            SourceType.NATIONAL_NEWS, ContentLanguage.ENGLISH, false, 2, null, true, 60, false);
    final NewsArticle article = new NewsArticle(UUID.randomUUID(), "State announces 120 new buses", "https://www.thehindu.com/a",
            Instant.parse("2026-11-03T05:00:00Z"), Instant.now(), ContentLanguage.ENGLISH, "h", true, List.of(), "TRANSPORT");

    private FactualClaim claim(String text, ClaimType type, VerificationStatus status, List<String> urls) {
        FactualClaim c = new FactualClaim(article.getId(), text, type, type == ClaimType.ATTRIBUTED_STATEMENT ? "Minister" : null);
        c.recordVerification(status, "TEST", urls.size(), urls, List.of(), null, Instant.now());
        return c;
    }

    @Test
    @DisplayName("Verified facts are stated, single-source facts attributed, unverified/opinion/conflicting left out")
    void buildsGroundedNotes() {
        var brief = ArticleBrief.build(article, source, List.of(
                claim("120 new buses were announced", ClaimType.STATISTIC, VerificationStatus.VERIFIED,
                        List.of("https://www.thehindu.com/a", "https://www.bbc.com/tamil/x")),
                claim("Buses will run on 15 new routes", ClaimType.FACT, VerificationStatus.PARTIALLY_VERIFIED,
                        List.of("https://www.thehindu.com/a")),
                claim("Minister said the fleet will double", ClaimType.ATTRIBUTED_STATEMENT, VerificationStatus.PARTIALLY_VERIFIED,
                        List.of("https://www.thehindu.com/a")),
                claim("This is a historic step", ClaimType.OPINION, VerificationStatus.UNVERIFIED, List.of()),
                claim("Cost is 500 crore", ClaimType.STATISTIC, VerificationStatus.CONFLICTING_INFORMATION, List.of()),
                claim("Some rumour", ClaimType.FACT, VerificationStatus.UNVERIFIED, List.of())), IST).orElseThrow();

        assertThat(brief.notes())
                .contains("VERIFIED FACTS").contains("120 new buses were announced")
                .contains("According to The Hindu – Tamil Nadu").contains("Buses will run on 15 new routes")
                .contains("always attribute to the speaker").contains("Minister said the fleet will double")
                .doesNotContain("historic step").doesNotContain("500 crore").doesNotContain("rumour");
        assertThat(brief.ceiling()).isEqualTo(VerificationStatus.PARTIALLY_VERIFIED);
        assertThat(brief.urls()).containsExactly("https://www.thehindu.com/a", "https://www.bbc.com/tamil/x");
        assertThat(brief.suggestedSlot()).isEqualTo(PostSlot.MORNING);
        assertThat(brief.usableClaims()).isEqualTo(3);
    }

    @Test
    void allVerifiedAllowsVerifiedCeiling() {
        var brief = ArticleBrief.build(article, source, List.of(
                claim("120 new buses were announced", ClaimType.FACT, VerificationStatus.VERIFIED, List.of("https://www.thehindu.com/a"))), IST);
        assertThat(brief.orElseThrow().ceiling()).isEqualTo(VerificationStatus.VERIFIED);
    }

    @Test
    void nothingUsableGivesEmpty() {
        assertThat(ArticleBrief.build(article, source, List.of(
                claim("Opinion", ClaimType.OPINION, VerificationStatus.UNVERIFIED, List.of()),
                claim("Disputed", ClaimType.FACT, VerificationStatus.CONFLICTING_INFORMATION, List.of())), IST)).isEmpty();
    }

    @Test
    @DisplayName("Draft status can reach VERIFIED only under a VERIFIED ceiling")
    void ceilingRules() {
        AiDraft d = new AiDraft("120 new buses announced for suburban routes.", "en", "TRANSPORT", List.of(), List.of("x"),
                List.of(), "VERIFIED", List.of(), 0.9);
        var verified = (DraftValidator.Accepted) DraftValidator.validate(d, ContentLanguage.ENGLISH, PostSlot.MORNING,
                List.of(), VerificationStatus.VERIFIED);
        var partial = (DraftValidator.Accepted) DraftValidator.validate(d, ContentLanguage.ENGLISH, PostSlot.MORNING,
                List.of(), VerificationStatus.PARTIALLY_VERIFIED);
        assertThat(verified.verificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(partial.verificationStatus()).isEqualTo(VerificationStatus.PARTIALLY_VERIFIED);

        AiDraft lower = new AiDraft("120 new buses announced.", "en", "TRANSPORT", List.of(), List.of(), List.of(),
                "UNVERIFIED", List.of(), 0.5);
        assertThat(((DraftValidator.Accepted) DraftValidator.validate(lower, ContentLanguage.ENGLISH, PostSlot.MORNING,
                List.of(), VerificationStatus.VERIFIED)).verificationStatus()).isEqualTo(VerificationStatus.UNVERIFIED);
    }
}
