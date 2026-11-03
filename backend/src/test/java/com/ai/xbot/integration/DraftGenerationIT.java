package com.ai.xbot.integration;

import static com.ai.xbot.support.StubOpenAiServer.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ai.xbot.support.StubOpenAiServer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end draft generation on real PostgreSQL with a local stand-in for OpenAI.
 * Verifies that generated drafts are saved only as GENERATED, carry only supplied sources,
 * and that failures save nothing.
 */
@AutoConfigureMockMvc
class DraftGenerationIT extends AbstractPostgresIT {

    static final StubOpenAiServer STUB;
    static {
        try { STUB = new StubOpenAiServer(); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @DynamicPropertySource
    static void openAi(DynamicPropertyRegistry r) {
        r.add("app.openai.api-key", () -> "sk-test-not-real");
        r.add("app.openai.model", () -> "test-model");
        r.add("app.openai.base-url", STUB::baseUrl);
        r.add("app.openai.timeout-seconds", () -> 2);
        r.add("app.openai.max-retries", () -> 1);
        r.add("app.openai.retry-backoff-ms", () -> 10);
    }

    @AfterAll
    static void stopStub() { STUB.close(); }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private static final String SOURCE = "https://www.tn.gov.in/press-release/42";

    @BeforeEach
    void reset() {
        STUB.reset();
        // FK-safe order, so this works whichever integration test class ran first
        for (String t : List.of("published_posts", "publishing_queue", "post_approvals", "post_sources", "generated_posts")) {
            jdbc.update("delete from " + t);
        }
    }

    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", "it-admin").param("password", "it-admin-password-123"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    private String body(String language, boolean withSources) {
        String sources = withSources
                ? ",\"sourceNotes\":\"The department announced 120 new buses for suburban routes.\",\"sourceUrls\":[\"" + SOURCE + "\"]"
                : "";
        return "{\"slot\":\"MORNING\",\"language\":\"" + language + "\",\"topic\":\"Bus services\"" + sources + "}";
    }

    @Test
    @DisplayName("Valid drafts are saved as GENERATED with only supplied sources; bad ones are discarded")
    void generatesAndPersists() throws Exception {
        STUB.reply(200, completion(drafts(
                draft("120 new buses announced for suburban routes. A welcome step for daily commuters. " + SOURCE,
                        "en", List.of(SOURCE, "https://invented.example/x"), "VERIFIED"),
                draft("Will the new suburban buses also cover late-night shifts for workers?", "en", List.of(SOURCE),
                        "PARTIALLY_VERIFIED"),
                draft("Read the full story at https://invented.example/story", "en", List.of(), "UNVERIFIED"))));

        MockHttpSession session = login();
        mvc.perform(post("/api/drafts/generate").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("en", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created.length()").value(2))
                .andExpect(jsonPath("$.discarded.length()").value(1))
                .andExpect(jsonPath("$.discarded[0].reason").value(org.hamcrest.Matchers.containsString("not supplied")))
                .andExpect(jsonPath("$.created[0].status").value("GENERATED"))
                .andExpect(jsonPath("$.created[0].verificationStatus").value("PARTIALLY_VERIFIED"))
                .andExpect(jsonPath("$.created[0].sourceUrls[0]").value(SOURCE))
                .andExpect(jsonPath("$.created[0].riskFlags").value(org.hamcrest.Matchers.hasItem("AI_URL_REMOVED")));

        assertThat(jdbc.queryForObject("select count(*) from generated_posts where status <> 'GENERATED'", Long.class)).isZero();
        assertThat(jdbc.queryForList("select distinct source_url from post_sources", String.class)).containsExactly(SOURCE);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action='DRAFT_GENERATED'", Long.class))
                .isGreaterThanOrEqualTo(2);

        // The API key only ever travels in the Authorization header to the OpenAI endpoint.
        assertThat(STUB.received().get(0).authorization()).isEqualTo("Bearer sk-test-not-real");
        assertThat(STUB.received().get(0).body()).doesNotContain("sk-test-not-real");

        String id = jdbc.queryForObject("select id from generated_posts limit 1", UUID.class).toString();
        mvc.perform(get("/api/drafts/" + id).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.promptVersion").value("v2"))
                .andExpect(jsonPath("$.aiModel").value("test-model"));
        mvc.perform(get("/api/drafts?status=GENERATED").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    @DisplayName("A pasted official TN DIPR release (dipr.tn.gov.in link) allows a VERIFIED draft; the prompt carries the voice")
    void officialSourceAllowsVerified() throws Exception {
        String dipr = "https://www.dipr.tn.gov.in/press/2026/11/release-1";
        STUB.reply(200, completion(drafts(draft("The Tamil Nadu government has launched a new scheme for farmers. #TVK", "en",
                List.of(dipr), "VERIFIED"))));
        mvc.perform(post("/api/drafts/generate").session(login()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slot\":\"MORNING\",\"language\":\"en\",\"sourceNotes\":\"Government launched a scheme for farmers.\","
                                + "\"sourceUrls\":[\"" + dipr + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created[0].verificationStatus").value("VERIFIED"));
        String system = STUB.received().get(0).body();
        assertThat(system).contains("TVK member").contains("#CMVijay");
        assertThat(jdbc.queryForObject("select count(*) from news_sources where base_url='https://www.dipr.tn.gov.in/' "
                + "and is_primary_source and feed_url is null", Long.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Generating the same draft again is detected as a duplicate and not saved")
    void duplicatesAreSkipped() throws Exception {
        String text = "What should be the top priority for public transport in Chennai this year?";
        STUB.reply(200, completion(drafts(draft(text, "en", List.of(), "UNVERIFIED"))));
        MockHttpSession session = login();
        mvc.perform(post("/api/drafts/generate").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body("en", false)))
                .andExpect(jsonPath("$.created.length()").value(1));
        mvc.perform(post("/api/drafts/generate").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("en", false)))
                .andExpect(jsonPath("$.created.length()").value(0))
                .andExpect(jsonPath("$.discarded[0].reason").value(org.hamcrest.Matchers.containsString("Identical")));
        assertThat(jdbc.queryForObject("select count(*) from generated_posts", Long.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Negative #7: OpenAI timeout returns 504 and saves nothing")
    void timeoutSavesNothing() throws Exception {
        STUB.reply(200, completion(drafts()), 5000);
        mvc.perform(post("/api/drafts/generate").session(login()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("en", false)))
                .andExpect(status().isGatewayTimeout());
        assertThat(jdbc.queryForObject("select count(*) from generated_posts", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action='AI_GENERATION_FAILED'", Long.class))
                .isPositive();
    }

    @Test
    @DisplayName("Drafts in the wrong language are discarded")
    void wrongLanguageDiscarded() throws Exception {
        STUB.reply(200, completion(drafts(draft("This is English, not Tamil.", "ta", List.of(), "UNVERIFIED"))));
        mvc.perform(post("/api/drafts/generate").session(login()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("ta", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created.length()").value(0))
                .andExpect(jsonPath("$.discarded[0].reason").value(org.hamcrest.Matchers.containsString("language")));
    }

    @Test
    @DisplayName("Input validation: bad slot, bad URL, disabled language")
    void validation() throws Exception {
        MockHttpSession session = login();
        mvc.perform(post("/api/drafts/generate").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"slot\":\"MIDNIGHT\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/drafts/generate").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"slot\":\"MORNING\",\"sourceUrls\":[\"javascript:alert(1)\"]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/drafts/generate").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"slot\":\"MORNING\",\"language\":\"mixed\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not enabled")));
        assertThat(STUB.received()).isEmpty();
    }

    @Test
    @DisplayName("Generation requires CSRF and login")
    void secured() throws Exception {
        mvc.perform(post("/api/drafts/generate").contentType(MediaType.APPLICATION_JSON).content(body("en", false)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/drafts")).andExpect(status().isUnauthorized());
    }
}
