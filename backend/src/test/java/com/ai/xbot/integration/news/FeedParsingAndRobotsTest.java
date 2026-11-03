package com.ai.xbot.integration.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeedParsingAndRobotsTest {

    static final String RSS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel><title>Test</title>
              <item><title>Chennai Metro phase 2 &amp; new stations</title><link>https://news.example/a?utm_source=x</link>
                <pubDate>Tue, 03 Nov 2026 10:00:00 +0530</pubDate><description><![CDATA[<p>The <b>state</b> announced work.</p>]]></description></item>
              <item><title>சென்னையில் புதிய பேருந்துகள்</title><link>https://news.example/b</link></item>
              <item><title>No link item</title></item>
              <item><title>Bad scheme</title><link>javascript:alert(1)</link></item>
            </channel></rss>""";

    static final String ATOM = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom"><title>Atom</title>
              <entry><title>Assembly session begins</title><link href="https://gov.example/p/1"/>
                <updated>2026-11-03T05:00:00Z</updated><summary>Session details</summary></entry>
            </feed>""";

    @Test
    void parsesRssStripsHtmlAndSkipsBadItems() throws Exception {
        List<FeedItem> items = FeedClient.parse(RSS.getBytes(StandardCharsets.UTF_8));
        assertThat(items).hasSize(2);
        assertThat(items.get(0).title()).isEqualTo("Chennai Metro phase 2 & new stations");
        assertThat(items.get(0).excerpt()).isEqualTo("The state announced work.");
        assertThat(items.get(0).publishedAt()).isNotNull();
        assertThat(items.get(1).title()).isEqualTo("சென்னையில் புதிய பேருந்துகள்");
    }

    @Test
    void parsesAtom() throws Exception {
        List<FeedItem> items = FeedClient.parse(ATOM.getBytes(StandardCharsets.UTF_8));
        assertThat(items).singleElement().satisfies(i -> {
            assertThat(i.link()).isEqualTo("https://gov.example/p/1");
            assertThat(i.publishedAt()).isNotNull();
        });
    }

    @Test
    void rejectsDoctypeToPreventXxe() {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE rss [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <rss version="2.0"><channel><title>&xxe;</title><item><title>&xxe;</title><link>https://a.example/</link></item></channel></rss>""";
        assertThatThrownBy(() -> FeedClient.parse(xxe.getBytes(StandardCharsets.UTF_8))).isInstanceOf(Exception.class);
    }

    @Test
    void robotsStarGroupApplies() {
        var rules = RobotsPolicy.parse("User-agent: *\nDisallow: /private/\nAllow: /private/feed.xml\n");
        assertThat(RobotsPolicy.allowed(rules, "/news/feed.rss")).isTrue();
        assertThat(RobotsPolicy.allowed(rules, "/private/x")).isFalse();
        assertThat(RobotsPolicy.allowed(rules, "/private/feed.xml")).isTrue(); // longest match wins
    }

    @Test
    void robotsOwnGroupOverridesStar() {
        var rules = RobotsPolicy.parse("User-agent: *\nDisallow: /\n\nUser-agent: XContentDeskBot\nDisallow: /admin\n");
        assertThat(RobotsPolicy.allowed(rules, "/feed.rss")).isTrue();
        assertThat(RobotsPolicy.allowed(rules, "/admin/x")).isFalse();
    }

    @Test
    void robotsDisallowAllAndWildcards() {
        assertThat(RobotsPolicy.allowed(RobotsPolicy.parse("User-agent: *\nDisallow: /"), "/feed")).isFalse();
        assertThat(RobotsPolicy.allowed(RobotsPolicy.parse("User-agent: *\nDisallow:"), "/feed")).isTrue();
        var rules = RobotsPolicy.parse("User-agent: *\nDisallow: /*.json$\nDisallow: /search*q=");
        assertThat(RobotsPolicy.allowed(rules, "/data/x.json")).isFalse();
        assertThat(RobotsPolicy.allowed(rules, "/data/x.json?y=1")).isTrue();
        assertThat(RobotsPolicy.allowed(rules, "/search?q=tn")).isFalse();
    }
}
