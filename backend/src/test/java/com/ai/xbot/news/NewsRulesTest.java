package com.ai.xbot.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ai.xbot.entity.enums.ClaimType;
import com.ai.xbot.entity.enums.VerificationStatus;
import com.ai.xbot.exception.BadRequestException;
import com.ai.xbot.news.VerificationRules.SourceInfo;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NewsRulesTest {

    static final SourceInfo HINDU = new SourceInfo("https://thehindu.com/a", "thehindu.com", false, 2);
    static final SourceInfo HINDU_OTHER_SECTION = new SourceInfo("https://thehindu.com/b", "thehindu.com", false, 2);
    static final SourceInfo BBC = new SourceInfo("https://bbc.com/tamil/x", "bbc.com", false, 2);
    static final SourceInfo GOV = new SourceInfo("https://tn.gov.in/pr/1", "tn.gov.in", true, 1);
    static final SourceInfo BLOG = new SourceInfo("https://smallsite.example/x", "smallsite.example", false, 3);

    // ---- VerificationRules ----
    @Test
    @DisplayName("One reputable publisher only: PARTIALLY_VERIFIED")
    void singleSource() {
        var d = VerificationRules.decide(ClaimType.FACT, HINDU, List.of(), List.of(), null);
        assertThat(d.status()).isEqualTo(VerificationStatus.PARTIALLY_VERIFIED);
        assertThat(d.supportingUrls()).containsExactly(HINDU.url());
    }

    @Test
    @DisplayName("Two independent reputable publishers: VERIFIED")
    void twoIndependent() {
        var d = VerificationRules.decide(ClaimType.STATISTIC, HINDU, List.of(BBC), List.of(), null);
        assertThat(d.status()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(d.method()).isEqualTo("INDEPENDENT_SOURCES");
        assertThat(d.independentSources()).isEqualTo(2);
    }

    @Test
    @DisplayName("Two sections of the same newspaper are not independent")
    void sameDomainNotIndependent() {
        var d = VerificationRules.decide(ClaimType.FACT, HINDU, List.of(HINDU_OTHER_SECTION), List.of(), null);
        assertThat(d.status()).isEqualTo(VerificationStatus.PARTIALLY_VERIFIED);
    }

    @Test
    @DisplayName("Official primary source: VERIFIED")
    void primarySource() {
        assertThat(VerificationRules.decide(ClaimType.FACT, GOV, List.of(), List.of(), null).status())
                .isEqualTo(VerificationStatus.VERIFIED);
        assertThat(VerificationRules.decide(ClaimType.FACT, HINDU, List.of(GOV), List.of(), null).method())
                .isEqualTo("PRIMARY_SOURCE");
    }

    @Test
    @DisplayName("Negative #9: conflicting sources win over support")
    void conflict() {
        var d = VerificationRules.decide(ClaimType.STATISTIC, HINDU, List.of(GOV), List.of(BBC), "different figure");
        assertThat(d.status()).isEqualTo(VerificationStatus.CONFLICTING_INFORMATION);
        assertThat(d.conflictingUrls()).containsExactly(BBC.url());
        assertThat(d.note()).contains("different figure");
    }

    @Test
    void opinionNeverVerified() {
        assertThat(VerificationRules.decide(ClaimType.OPINION, GOV, List.of(BBC), List.of(), null).status())
                .isEqualTo(VerificationStatus.UNVERIFIED);
    }

    @Test
    void lowReliabilityOnlyIsUnverified() {
        assertThat(VerificationRules.decide(ClaimType.FACT, BLOG, List.of(), List.of(), null).status())
                .isEqualTo(VerificationStatus.UNVERIFIED);
        // A low-tier corroborator does not upgrade a single reputable source
        assertThat(VerificationRules.decide(ClaimType.FACT, HINDU, List.of(BLOG), List.of(), null).status())
                .isEqualTo(VerificationStatus.PARTIALLY_VERIFIED);
    }

    // ---- RelevanceFilter ----
    @Test
    void englishAndTamilRelevance() {
        assertThat(RelevanceFilter.evaluate("Chennai corporation clears waterlogging", "", true).relevant()).isTrue();
        assertThat(RelevanceFilter.evaluate("திமுக, அதிமுக தலைவர்கள் சந்திப்பு", "", true).relevant()).isTrue();
        assertThat(RelevanceFilter.evaluate("Football world cup final tonight", "", true).relevant()).isFalse();
        assertThat(RelevanceFilter.evaluate("Football world cup final tonight", "", false).relevant()).isTrue();
    }

    @Test
    void shortAcronymsMatchWholeWordsOnly() {
        assertThat(RelevanceFilter.evaluate("Vendmkart opens a store", "", true).relevant()).isFalse();
        assertThat(RelevanceFilter.evaluate("DMK announces candidates", "", true).relevant()).isTrue();
    }

    @Test
    void categories() {
        assertThat(RelevanceFilter.evaluate("TVK holds state conference in Madurai", "", true).category()).isEqualTo("TVK");
        assertThat(RelevanceFilter.evaluate("Tamil Nadu Assembly passes bill", "", true).category()).isEqualTo("LEGISLATURE");
        assertThat(RelevanceFilter.evaluate("சென்னை மெட்ரோ ரயில் பணிகள்", "", true).category()).isEqualTo("TRANSPORT");
    }

    // ---- UrlNormalizer ----
    @Test
    void dropsTrackingParamsAndFragments() {
        assertThat(UrlNormalizer.normalize("https://WWW.News.example/a/b?utm_source=x&id=5&fbclid=z#top"))
                .isEqualTo("https://www.news.example/a/b?id=5");
        assertThat(UrlNormalizer.domain("https://www.thehindu.com/news")).isEqualTo("thehindu.com");
    }

    // ---- SourceRegistry ----
    @Test
    @DisplayName("Social media and blog platforms cannot be registered as sources")
    void socialMediaBlocked() {
        assertThatThrownBy(() -> SourceRegistryService.checkUrl("https://x.com/someone", "Feed"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Social media");
        assertThatThrownBy(() -> SourceRegistryService.checkUrl("https://news.blogspot.com/feed", "Feed"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> SourceRegistryService.checkUrl("ftp://a.example/", "Feed"))
                .isInstanceOf(BadRequestException.class);
        SourceRegistryService.checkUrl("https://www.thehindu.com/feeder/default.rss", "Feed");
    }
}
