package com.ai.xbot.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ai.xbot.support.StubOpenAiServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full news pipeline on real PostgreSQL: registry → fetch (robots.txt, conditional GET, failures)
 * → AI analysis → cross-source verification → conflict → drafting from verified claims.
 * Two fake publishers run on different host names (127.0.0.1 and localhost) so they count as independent.
 * No real network or OpenAI calls are made.
 */
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NewsPipelineIT extends AbstractPostgresIT {

    static final StubOpenAiServer AI;
    static final HttpServer FEEDS;
    static final AtomicBoolean CONTRADICT = new AtomicBoolean(false);
    static final ObjectMapper OM = new ObjectMapper();

    static final String TITLE_A = "Tamil Nadu announces 120 new buses for Chennai suburban routes";
    static final String TITLE_B = "Chennai to get 120 new buses on suburban routes, says Transport Minister";
    static String urlA, urlB;

    static {
        try {
            AI = new StubOpenAiServer();
            FEEDS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            String now = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now());
            FEEDS.createContext("/robots.txt", ex -> send(ex, 200, "User-agent: *\nDisallow: /private/\n", null));
            FEEDS.createContext("/a/feed.rss", ex -> {
                if ("\"v1\"".equals(ex.getRequestHeaders().getFirst("If-None-Match"))) { send(ex, 304, "", "\"v1\""); return; }
                send(ex, 200, rss(item(TITLE_A, "http://127.0.0.1:" + port() + "/a/story-1?utm_source=rss", now,
                                "The state transport department said the buses will serve suburban Chennai."),
                        item("Cricket: league final tonight", "http://127.0.0.1:" + port() + "/a/sport", now, "Sports news")), "\"v1\"");
            });
            FEEDS.createContext("/b/feed.rss", ex -> send(ex, 200, rss(item(TITLE_B, "http://localhost:" + port() + "/b/story-9", now,
                    "The minister said 120 buses will be added to suburban routes in Chennai.")), null));
            FEEDS.createContext("/private/feed.rss", ex -> send(ex, 200, rss(item("Tamil Nadu secret", "http://127.0.0.1/x", now, "")), null));
            FEEDS.start();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        urlA = "http://127.0.0.1:" + port() + "/a/story-1";
        urlB = "http://localhost:" + port() + "/b/story-9";

        AI.handler(body -> {
            if (body.contains("article_analysis")) {
                boolean isB = body.contains(TITLE_B);
                String claims = "[{\"text\":\"The Tamil Nadu government announced 120 new buses for Chennai suburban routes\","
                        + "\"type\":\"STATISTIC\",\"attributed_to\":null}"
                        + (isB ? ",{\"text\":\"Transport Minister said the buses will be added to suburban routes\","
                        + "\"type\":\"ATTRIBUTED_STATEMENT\",\"attributed_to\":\"Transport Minister\"}" : "")
                        + ",{\"text\":\"This is a big relief for commuters\",\"type\":\"OPINION\",\"attributed_to\":null}]";
                return new StubOpenAiServer.Reply(200, StubOpenAiServer.completion("{\"summary\":\"Tamil Nadu adds 120 new buses "
                        + "for suburban routes in Chennai.\",\"category\":\"TRANSPORT\",\"claims\":" + claims + "}"), 0);
            }
            if (body.contains("claim_verification")) {
                String rel = CONTRADICT.get() ? "\"supports\":[],\"contradicts\":[0]" : "\"supports\":[0],\"contradicts\":[]";
                String assessments = "[{\"claim_index\":0," + rel + ",\"note\":\"same figure\"},"
                        + "{\"claim_index\":1," + rel + ",\"note\":\"same statement\"},"
                        + "{\"claim_index\":2,\"supports\":[0],\"contradicts\":[],\"note\":\"\"}]";
                return new StubOpenAiServer.Reply(200, StubOpenAiServer.completion("{\"assessments\":" + assessments + "}"), 0);
            }
            return new StubOpenAiServer.Reply(200, StubOpenAiServer.completion(StubOpenAiServer.drafts(
                    StubOpenAiServer.draft("Tamil Nadu adds 120 new buses for Chennai's suburban routes. Will they cover late-night shifts too?",
                            "en", java.util.List.of(urlA), "VERIFIED"))), 0);
        });
    }

    static int port() { return FEEDS.getAddress().getPort(); }

    static String item(String title, String link, String date, String desc) {
        return "<item><title>" + title + "</title><link>" + link + "</link><pubDate>" + date + "</pubDate><description>"
                + desc + "</description></item>";
    }

    static String rss(String... items) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><rss version=\"2.0\"><channel><title>t</title>"
                + String.join("", items) + "</channel></rss>";
    }

    static void send(com.sun.net.httpserver.HttpExchange ex, int status, String body, String etag) throws java.io.IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        if (etag != null) ex.getResponseHeaders().add("ETag", etag);
        ex.sendResponseHeaders(status, status == 304 ? -1 : b.length);
        if (status != 304) try (var os = ex.getResponseBody()) { os.write(b); }
        ex.close();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("app.openai.api-key", () -> "sk-test-not-real");
        r.add("app.openai.model", () -> "test-model");
        r.add("app.openai.base-url", AI::baseUrl);
        r.add("app.openai.max-retries", () -> 0);
    }

    @AfterAll
    static void stop() { AI.close(); FEEDS.stop(0); }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    static MockHttpSession session;
    static UUID articleA, articleB;

    MockHttpSession login() throws Exception {
        if (session == null) {
            session = (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                    .param("username", "it-admin").param("password", "it-admin-password-123"))
                    .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        }
        return session;
    }

    String source(String name, String base, String feed, boolean keywordMatch) {
        return "{\"name\":\"" + name + "\",\"baseUrl\":\"" + base + "\",\"feedUrl\":\"" + feed + "\",\"sourceType\":\"NATIONAL_NEWS\","
                + "\"language\":\"en\",\"primarySource\":false,\"reliabilityTier\":2,\"enabled\":true,\"fetchIntervalMinutes\":60,"
                + "\"requiresKeywordMatch\":" + keywordMatch + "}";
    }

    JsonNode json(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return OM.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test @Order(1)
    @DisplayName("Seeded real sources are present; social media can't be registered")
    void registry() throws Exception {
        assertThat(jdbc.queryForObject("select count(*) from news_sources where feed_url like 'https://www.thehindu.com/%'", Long.class))
                .isEqualTo(2);
        jdbc.update("update news_sources set enabled = false"); // tests must never hit the real internet
        mvc.perform(post("/api/news/sources").session(login()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(source("Someone on X", "https://x.com/someone", "https://x.com/someone/rss", true)))
                .andExpect(status().isBadRequest());

        String p = "http://127.0.0.1:" + port();
        String l = "http://localhost:" + port();
        for (String body : new String[]{
                source("Publisher A", p + "/a/", p + "/a/feed.rss", true),
                source("Publisher B", l + "/b/", l + "/b/feed.rss", true),
                source("Robots-blocked", p + "/private/", p + "/private/feed.rss", true),
                source("Offline site", "http://127.0.0.1:1/", "http://127.0.0.1:1/feed.rss", true)}) {
            mvc.perform(post("/api/news/sources").session(login()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());
        }
    }

    @Test @Order(2)
    @DisplayName("Fetch: new relevant articles stored; robots.txt honoured; Negative #8 offline source reported, others continue")
    void fetch() throws Exception {
        JsonNode run = json(mvc.perform(post("/api/news/fetch").session(login()).with(csrf())).andExpect(status().isOk()));
        assertThat(run.path("newArticles").asInt()).isEqualTo(3);
        assertThat(run.path("relevantArticles").asInt()).isEqualTo(2);
        String statuses = run.path("sources").toString();
        assertThat(statuses).contains("BLOCKED_BY_ROBOTS").contains("NETWORK_ERROR");

        assertThat(jdbc.queryForObject("select count(*) from news_articles where original_url like '%utm_%'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from news_articles where title = 'Tamil Nadu secret'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select consecutive_failures from news_sources where name='Offline site'", Integer.class)).isEqualTo(1);

        articleA = jdbc.queryForObject("select id from news_articles where original_url = ?", UUID.class, urlA);
        articleB = jdbc.queryForObject("select id from news_articles where original_url = ?", UUID.class, urlB);

        // Second fetch: conditional GET (304) for A and duplicate detection for B
        JsonNode again = json(mvc.perform(post("/api/news/fetch").session(login()).with(csrf())));
        assertThat(again.path("newArticles").asInt()).isZero();
        assertThat(again.path("sources").toString()).contains("NOT_MODIFIED");
    }

    @Test @Order(3)
    @DisplayName("Single publisher: PARTIALLY_VERIFIED; opinions stay UNVERIFIED")
    void analyseFirst() throws Exception {
        JsonNode a = json(mvc.perform(post("/api/news/articles/" + articleA + "/analyse").session(login()).with(csrf()))
                .andExpect(status().isOk()));
        assertThat(a.path("analysisStatus").asText()).isEqualTo("ANALYSED");
        assertThat(a.path("summary").asText()).contains("120 new buses");
        assertThat(statusOf(a, "STATISTIC")).isEqualTo("PARTIALLY_VERIFIED");
        assertThat(statusOf(a, "OPINION")).isEqualTo("UNVERIFIED");
    }

    @Test @Order(4)
    @DisplayName("Independent publisher reports the same fact: VERIFIED with both URLs")
    void corroborate() throws Exception {
        JsonNode b = json(mvc.perform(post("/api/news/articles/" + articleB + "/analyse").session(login()).with(csrf())));
        assertThat(statusOf(b, "STATISTIC")).isEqualTo("VERIFIED");
        assertThat(claim(b, "STATISTIC").path("supportingUrls").toString()).contains(urlA).contains(urlB);
        assertThat(statusOf(b, "OPINION")).isEqualTo("UNVERIFIED");

        JsonNode a = json(mvc.perform(post("/api/news/articles/" + articleA + "/verify").session(login()).with(csrf())));
        assertThat(statusOf(a, "STATISTIC")).isEqualTo("VERIFIED");
        assertThat(claim(a, "STATISTIC").path("verificationMethod").asText()).isEqualTo("INDEPENDENT_SOURCES");
    }

    @Test @Order(5)
    @DisplayName("Draft from a fully verified article can be VERIFIED and records the article it came from")
    void draftFromArticle() throws Exception {
        JsonNode r = json(mvc.perform(post("/api/news/articles/" + articleA + "/draft").session(login()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"language\":\"en\"}"))
                .andExpect(status().isOk()));
        assertThat(r.path("created").size()).isEqualTo(1);
        JsonNode d = r.path("created").get(0);
        assertThat(d.path("status").asText()).isEqualTo("GENERATED");
        assertThat(d.path("verificationStatus").asText()).isEqualTo("VERIFIED");
        assertThat(d.path("slot").asText()).isEqualTo("MORNING"); // TRANSPORT -> morning slot
        UUID postId = UUID.fromString(d.path("id").asText());
        assertThat(jdbc.queryForObject("select count(*) from post_sources where post_id=? and article_id=?", Long.class, postId, articleA))
                .isPositive();
        // The prompt sent to the AI contained verified facts and excluded the opinion
        String lastPrompt = AI.received().get(AI.received().size() - 1).body();
        assertThat(lastPrompt).contains("VERIFIED FACTS").doesNotContain("big relief");

        JsonNode article = json(mvc.perform(get("/api/news/articles/" + articleA).session(login())));
        assertThat(article.path("usedInDraft").asBoolean()).isTrue();
    }

    @Test @Order(6)
    @DisplayName("Negative #9: conflicting sources mark claims CONFLICTING and they can't be drafted")
    void conflict() throws Exception {
        CONTRADICT.set(true);
        try {
            JsonNode b = json(mvc.perform(post("/api/news/articles/" + articleB + "/verify").session(login()).with(csrf())));
            assertThat(statusOf(b, "STATISTIC")).isEqualTo("CONFLICTING_INFORMATION");
            assertThat(claim(b, "STATISTIC").path("conflictingUrls").toString()).contains(urlA);
            mvc.perform(post("/api/news/articles/" + articleB + "/draft").session(login()).with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("no verified")));
        } finally {
            CONTRADICT.set(false);
        }
    }

    @Test @Order(7)
    void irrelevantArticlesCannotBeAnalysed() throws Exception {
        UUID sport = jdbc.queryForObject("select id from news_articles where title like 'Cricket%'", UUID.class);
        mvc.perform(post("/api/news/articles/" + sport + "/analyse").session(login()).with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/news/articles?relevantOnly=true").session(login()))
                .andExpect(jsonPath("$.items[*].title").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.startsWith("Cricket")))));
    }

    @Test @Order(8)
    void newsWritesNeedCsrf() throws Exception {
        mvc.perform(post("/api/news/fetch").session(login())).andExpect(status().isForbidden());
    }

    static JsonNode claim(JsonNode article, String type) {
        for (JsonNode c : article.path("claims")) if (c.path("type").asText().equals(type)) return c;
        throw new AssertionError("No claim of type " + type + " in " + article);
    }

    static String statusOf(JsonNode article, String type) {
        return claim(article, type).path("verificationStatus").asText();
    }
}
