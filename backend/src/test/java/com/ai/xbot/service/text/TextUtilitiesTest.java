package com.ai.xbot.service.text;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.xbot.entity.enums.ContentLanguage;
import org.junit.jupiter.api.Test;

class TextUtilitiesTest {

    // ---- XTextLength ----
    @Test
    void latinAndTamilWeighOneEach() {
        assertThat(XTextLength.weightedLength("abc")).isEqualTo(3);
        assertThat(XTextLength.weightedLength("தமிழ்")).isEqualTo("தமிழ்".codePointCount(0, "தமிழ்".length()));
    }

    @Test
    void urlsCountAs23() {
        assertThat(XTextLength.weightedLength("see https://www.tn.gov.in/some/very/long/path/to/a/page.html"))
                .isEqualTo(4 + 23);
    }

    @Test
    void emojiAndCjkWeighTwo() {
        assertThat(XTextLength.weightedLength("👍")).isEqualTo(2);
        assertThat(XTextLength.weightedLength("中")).isEqualTo(2);
    }

    @Test
    void limitIs280() {
        assertThat(XTextLength.fits("a".repeat(280))).isTrue();
        assertThat(XTextLength.fits("a".repeat(281))).isFalse();
    }

    // ---- LanguageDetector ----
    @Test
    void tamilScriptPostIsTamil() {
        String ta = "சென்னை மெட்ரோ இரண்டாம் கட்ட பணிகள் எப்போது முடியும்? #Chennai";
        assertThat(LanguageDetector.matches(ta, ContentLanguage.TAMIL)).isTrue();
        assertThat(LanguageDetector.matches(ta, ContentLanguage.ENGLISH)).isFalse();
    }

    @Test
    void englishPostIsEnglish() {
        String en = "When will Chennai Metro phase 2 be completed? Commuters deserve a clear timeline.";
        assertThat(LanguageDetector.matches(en, ContentLanguage.ENGLISH)).isTrue();
        assertThat(LanguageDetector.matches(en, ContentLanguage.TAMIL)).isFalse();
    }

    @Test
    void romanTanglishIsMixed() {
        String mixed = "Metro work eppo mudiyum nu theriyala, commuters ku oru clear timeline venum.";
        assertThat(LanguageDetector.matches(mixed, ContentLanguage.MIXED)).isTrue();
    }

    @Test
    void tamilScriptWithEnglishWordsIsMixed() {
        String mixed = "Metro project status என்ன? Public transport improve ஆகணும், timeline clear-ஆ சொல்லணும்.";
        assertThat(LanguageDetector.matches(mixed, ContentLanguage.MIXED)).isTrue();
        assertThat(LanguageDetector.matches(mixed, ContentLanguage.ENGLISH)).isFalse();
    }

    @Test
    void hashtagsAndUrlsDoNotChangeLanguage() {
        String ta = "பள்ளிக் கல்வி பற்றிய புதிய அறிவிப்பு https://example.org/news #Education #TamilNadu";
        assertThat(LanguageDetector.matches(ta, ContentLanguage.TAMIL)).isTrue();
    }

    @Test
    void noLettersMatchesNothing() {
        assertThat(LanguageDetector.matches("123 !!!", ContentLanguage.ENGLISH)).isFalse();
    }

    // ---- TextSimilarity ----
    @Test
    void sameWordsDifferentLinkAreIdentical() {
        assertThat(TextSimilarity.jaccard("Bus fares revised https://a.example", "bus fares REVISED https://b.example"))
                .isEqualTo(1.0);
    }

    @Test
    void tamilTokensCompareCorrectly() {
        assertThat(TextSimilarity.jaccard("சென்னை மெட்ரோ பணிகள்", "சென்னை மெட்ரோ பணிகள்")).isEqualTo(1.0);
        assertThat(TextSimilarity.jaccard("சென்னை மெட்ரோ பணிகள்", "மதுரை விமான நிலையம்")).isEqualTo(0.0);
    }
}
