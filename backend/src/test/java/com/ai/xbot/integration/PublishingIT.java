package com.ai.xbot.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ai.xbot.entity.GeneratedPost;
import com.ai.xbot.entity.enums.ContentLanguage;
import com.ai.xbot.entity.enums.PostSlot;
import com.ai.xbot.repository.GeneratedPostRepository;
import com.ai.xbot.service.ContentHasher;
import com.ai.xbot.service.publishing.PublisherService;
import com.ai.xbot.support.StubXServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Approval workflow and X publishing against a local X API stand-in, on real PostgreSQL.
 * Covers the spec's critical negative scenarios 1-6 and 10-12. The rule under test throughout:
 * nothing reaches X unless a human approved exactly that text.
 */
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PublishingIT extends AbstractPostgresIT {

    static final StubXServer X;
    static final String KEY;
    static {
        try { X = new StubXServer(); } catch (Exception e) { throw new IllegalStateException(e); }
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        KEY = Base64.getEncoder().encodeToString(b);
    }
    static final ObjectMapper OM = new ObjectMapper();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("app.x.client-id", () -> "client-123");
        r.add("app.x.client-secret", () -> "secret-456");
        r.add("app.x.redirect-uri", () -> "http://localhost:3000/api/x/callback");
        r.add("app.x.token-encryption-key", () -> KEY);
        r.add("app.x.api-base-url", X::apiBase);
        r.add("app.x.authorize-url", () -> "https://x.example/i/oauth2/authorize");
    }

    @AfterAll
    static void stop() { X.close(); }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired GeneratedPostRepository posts;
    @Autowired ContentHasher hasher;
    @Autowired PublisherService publisher;

    static MockHttpSession session;

    MockHttpSession login() throws Exception {
        if (session == null) {
            session = (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                    .param("username", "it-admin").param("password", "it-admin-password-123"))
                    .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        }
        return session;
    }

    JsonNode json(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return OM.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    UUID newDraft(String text) {
        return posts.save(new GeneratedPost(text, hasher.hash(text), ContentLanguage.ENGLISH, "GOVERNANCE",
                PostSlot.MORNING, Instant.now().plusSeconds(3600))).getId();
    }

    long version(UUID id) throws Exception {
        return json(mvc.perform(get("/api/drafts/" + id).session(login()))).path("version").asLong();
    }

    org.springframework.test.web.servlet.ResultActions approve(UUID id, String extra) throws Exception {
        return mvc.perform(post("/api/drafts/" + id + "/approve").session(login()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":" + version(id) + (extra.isEmpty() ? "" : "," + extra) + "}"));
    }

    String postStatus(UUID id) {
        return jdbc.queryForObject("select status from generated_posts where id=?", String.class, id);
    }

    String queueStatus(UUID id) {
        return jdbc.queryForObject("select status from publishing_queue where post_id=? order by created_at desc limit 1", String.class, id);
    }

    void makeDue(UUID id) {
        jdbc.update("update publishing_queue set scheduled_at = now(), next_attempt_at = null where post_id=? and status='PENDING'", id);
    }

    void setPublishing(boolean on) throws Exception {
        mvc.perform(put("/api/settings/publishing").session(login()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":" + on + "}")).andExpect(status().isOk());
    }

    String connect() throws Exception {
        String location = mvc.perform(get("/api/x/connect").session(login()))
                .andExpect(status().isFound()).andReturn().getResponse().getRedirectedUrl();
        var q = UriComponentsBuilder.fromUriString(location).build().getQueryParams();
        assertThat(location).startsWith("https://x.example/i/oauth2/authorize");
        assertThat(q.getFirst("code_challenge_method")).isEqualTo("S256");
        assertThat(q.getFirst("client_id")).isEqualTo("client-123");
        assertThat(java.net.URLDecoder.decode(q.getFirst("scope"), StandardCharsets.UTF_8))
                .isEqualTo("tweet.read tweet.write users.read offline.access");
        return q.getFirst("state");
    }

    // ------------------------------------------------------------------ connect

    @Test @Order(1)
    @DisplayName("OAuth PKCE: bad state is refused; good state stores encrypted tokens")
    void connectX() throws Exception {
        String state = connect();
        mvc.perform(get("/api/x/callback").session(login()).param("state", "forged").param("code", "c"))
                .andExpect(redirectedUrl("/settings?x=error&reason=state"));
        // state is single-use: the real one no longer works after a failed attempt
        mvc.perform(get("/api/x/callback").session(login()).param("state", state).param("code", "c"))
                .andExpect(redirectedUrl("/settings?x=error&reason=state"));

        String state2 = connect();
        mvc.perform(get("/api/x/callback").session(login()).param("state", state2).param("code", "auth-code"))
                .andExpect(redirectedUrl("/settings?x=connected"));

        StubXServer.Call tokenCall = X.last("/2/oauth2/token");
        assertThat(tokenCall.authorization()).isEqualTo("Basic " + Base64.getEncoder()
                .encodeToString("client-123:secret-456".getBytes(StandardCharsets.UTF_8)));
        assertThat(tokenCall.body()).contains("code_verifier=").contains("grant_type=authorization_code");

        Map<String, Object> row = jdbc.queryForMap("select * from connected_accounts where provider_user_id='777'");
        assertThat((String) row.get("access_token_encrypted")).startsWith("v1:").doesNotContain("acc-");
        assertThat((String) row.get("refresh_token_encrypted")).startsWith("v1:").doesNotContain("ref-");
        mvc.perform(get("/api/x/status").session(login()))
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.username").value("vijay_test"));
        mvc.perform(get("/api/system/status").session(login())).andExpect(jsonPath("$.xAccountConnected").value(true));
    }

    // ------------------------------------------------------------------ approval workflow

    @Test @Order(2)
    @DisplayName("Approve with switch off: scheduled but NOT sent; switch on: sent exactly once")
    void approveAndPublish() throws Exception {
        UUID id = newDraft("Chennai suburban bus services: what should be the first priority?");
        approve(id, "\"publishNow\":true").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.publishing.approvedBy").value("it-admin"));
        long before = X.count("/2/tweets");

        publisher.runOnce();
        assertThat(X.count("/2/tweets")).isEqualTo(before);
        assertThat(postStatus(id)).isEqualTo("SCHEDULED");

        setPublishing(true);
        makeDue(id);
        assertThat(publisher.runOnce()).isEqualTo(1);
        assertThat(X.count("/2/tweets")).isEqualTo(before + 1);
        assertThat(X.last("/2/tweets").authorization()).startsWith("Bearer acc-");
        assertThat(postStatus(id)).isEqualTo("PUBLISHED");

        JsonNode d = json(mvc.perform(get("/api/drafts/" + id).session(login())));
        assertThat(d.path("publishing").path("xUrl").asText()).startsWith("https://x.com/vijay_test/status/");

        // Negative #4: running again or approving again never republishes
        publisher.runOnce();
        assertThat(X.count("/2/tweets")).isEqualTo(before + 1);
        approve(id, "\"publishNow\":true").andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from published_posts where post_id=?", Long.class, id)).isEqualTo(1);
    }

    @Test @Order(3)
    @DisplayName("Negative #1: unapproved drafts and forged queue rows never reach X")
    void unapprovedNeverPublished() throws Exception {
        UUID generated = newDraft("A draft nobody approved should never be posted.");
        UUID forged = newDraft("A forged queue entry should never be posted either.");
        UUID userId = jdbc.queryForObject("select id from users limit 1", UUID.class);
        UUID approval = jdbc.queryForObject("insert into post_approvals (post_id, decision, decided_by, approved_content_hash, "
                + "invalidated_at) values (?, 'APPROVED', ?, 'h', now()) returning id", UUID.class, forged, userId);
        jdbc.update("update generated_posts set status='SCHEDULED' where id=?", forged);
        jdbc.update("insert into publishing_queue (post_id, approval_id, scheduled_at, idempotency_key) values (?, ?, now(), ?)",
                forged, approval, "forged-" + forged);
        long before = X.count("/2/tweets");

        publisher.runOnce();
        assertThat(X.count("/2/tweets")).isEqualTo(before);
        assertThat(postStatus(generated)).isEqualTo("GENERATED");
        assertThat(queueStatus(forged)).isEqualTo("CANCELLED");
        assertThat(postStatus(forged)).isEqualTo("UNDER_REVIEW");
    }

    @Test @Order(4)
    @DisplayName("Negative #2: editing after approval invalidates it; tampering with the DB is caught")
    void editAfterApproval() throws Exception {
        UUID id = newDraft("Original approved text about Metro phase 2.");
        approve(id, "\"publishAt\":\"" + Instant.now().plusSeconds(600) + "\"").andExpect(status().isOk());

        mvc.perform(put("/api/drafts/" + id).session(login()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Edited text about Metro phase 2.\",\"version\":" + version(id) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.editedByHuman").value(true));
        assertThat(queueStatus(id)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select count(*) from post_approvals where post_id=? and invalidated_at is null "
                + "and decision='APPROVED'", Long.class, id)).isZero();

        // Approve again (edited → warning must be acknowledged), then change the text behind the app's back
        approve(id, "\"publishNow\":true").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("edited")));
        approve(id, "\"publishNow\":true,\"acknowledgeWarnings\":true").andExpect(status().isOk());
        jdbc.update("update generated_posts set content='Tampered text', content_hash=? where id=?", hasher.hash("Tampered text"), id);
        long before = X.count("/2/tweets");
        publisher.runOnce();
        assertThat(X.count("/2/tweets")).isEqualTo(before);
        assertThat(postStatus(id)).isEqualTo("UNDER_REVIEW");
        assertThat(queueStatus(id)).isEqualTo("CANCELLED");
    }

    @Test @Order(5)
    @DisplayName("Negative #3: a rejected draft can't be approved or published")
    void rejected() throws Exception {
        UUID id = newDraft("Draft that will be rejected.");
        mvc.perform(post("/api/drafts/" + id + "/reject").session(login()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + version(id) + ",\"reason\":\"Off topic\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Off topic"));
        approve(id, "\"publishNow\":true").andExpect(status().isConflict());
        mvc.perform(put("/api/drafts/" + id).session(login()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"try\",\"version\":" + version(id) + "}")).andExpect(status().isConflict());
    }

    @Test @Order(6)
    @DisplayName("Stale version is refused (someone else changed the draft)")
    void staleVersion() throws Exception {
        UUID id = newDraft("Versioned draft.");
        long v = version(id);
        mvc.perform(put("/api/drafts/" + id).session(login()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Versioned draft, edited.\",\"version\":" + v + "}")).andExpect(status().isOk());
        mvc.perform(post("/api/drafts/" + id + "/approve").session(login()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":" + v + ",\"publishNow\":true,\"acknowledgeWarnings\":true}")).andExpect(status().isConflict());
    }

    @Test @Order(7)
    @DisplayName("Negative #10: two publisher runs at once send the post only once")
    void concurrentRuns() throws Exception {
        UUID id = newDraft("Concurrency check: this must appear on X exactly once.");
        approve(id, "\"publishNow\":true").andExpect(status().isOk());
        long before = X.count("/2/tweets");
        X.tweetDelayMs = 300;
        try {
            ExecutorService pool = Executors.newFixedThreadPool(3);
            List<Future<Integer>> f = new ArrayList<>();
            for (int i = 0; i < 3; i++) f.add(pool.submit(publisher::runOnce));
            int total = 0;
            for (Future<Integer> x : f) total += x.get(30, TimeUnit.SECONDS);
            pool.shutdown();
            assertThat(total).isEqualTo(1);
        } finally {
            X.tweetDelayMs = 0;
        }
        assertThat(X.count("/2/tweets")).isEqualTo(before + 1);
        assertThat(postStatus(id)).isEqualTo("PUBLISHED");
    }

    @Test @Order(8)
    @DisplayName("Negative #5: HTTP 429 is retried later with backoff, then succeeds")
    void rateLimited() throws Exception {
        UUID id = newDraft("Rate limit check post.");
        approve(id, "\"publishNow\":true").andExpect(status().isOk());
        X.tweetReplies.add(new StubXServer.Reply(429, "{\"title\":\"Too Many Requests\"}", Map.of("retry-after", "120")));
        publisher.runOnce();
        assertThat(postStatus(id)).isEqualTo("SCHEDULED");
        Map<String, Object> q = jdbc.queryForMap("select status, attempt_count, next_attempt_at from publishing_queue where post_id=?", id);
        assertThat(q.get("status")).isEqualTo("PENDING");
        assertThat(q.get("attempt_count")).isEqualTo(1);
        assertThat(((java.sql.Timestamp) q.get("next_attempt_at")).toInstant()).isAfter(Instant.now().plusSeconds(100));

        publisher.runOnce(); // not due yet
        assertThat(postStatus(id)).isEqualTo("SCHEDULED");
        jdbc.update("update publishing_queue set next_attempt_at = now() where post_id=?", id);
        publisher.runOnce();
        assertThat(postStatus(id)).isEqualTo("PUBLISHED");
    }

    @Test @Order(9)
    @DisplayName("Negative #6 / #11: 401 or an expired token triggers a refresh; refresh tokens rotate")
    void tokenRefresh() throws Exception {
        UUID id = newDraft("Token refresh check post.");
        approve(id, "\"publishNow\":true").andExpect(status().isOk());
        X.tweetReplies.add(new StubXServer.Reply(401, "{\"title\":\"Unauthorized\"}", Map.of()));
        long refreshesBefore = X.calls.stream().filter(c -> c.path().equals("/2/oauth2/token") && c.body().contains("refresh_token")).count();
        publisher.runOnce();
        assertThat(postStatus(id)).isEqualTo("PUBLISHED");
        long refreshesAfter = X.calls.stream().filter(c -> c.path().equals("/2/oauth2/token") && c.body().contains("refresh_token")).count();
        assertThat(refreshesAfter).isEqualTo(refreshesBefore + 1);

        // Expiring token is refreshed before sending
        jdbc.update("update connected_accounts set token_expires_at = now() - interval '1 minute' where provider_user_id='777'");
        UUID id2 = newDraft("Expired token check post.");
        approve(id2, "\"publishNow\":true").andExpect(status().isOk());
        publisher.runOnce();
        assertThat(postStatus(id2)).isEqualTo("PUBLISHED");
        String lastToken = X.last("/2/tweets").authorization();
        String lastIssued = X.calls.stream().filter(c -> c.path().equals("/2/oauth2/token")).count() + "";
        assertThat(lastToken).isEqualTo("Bearer acc-" + lastIssued);
    }

    @Test @Order(10)
    @DisplayName("Duplicate-content 403 fails without retry; user retry re-queues the same approval")
    void duplicateAndRetry() throws Exception {
        UUID id = newDraft("Duplicate content check post.");
        approve(id, "\"publishNow\":true").andExpect(status().isOk());
        X.tweetReplies.add(new StubXServer.Reply(403, "{\"detail\":\"You are not allowed to create a Tweet with duplicate content.\"}", Map.of()));
        publisher.runOnce();
        assertThat(postStatus(id)).isEqualTo("FAILED");
        assertThat(queueStatus(id)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select count(*) from notification_logs where type='PUBLISH_FAILURE' and related_entity_id=?",
                Long.class, id)).isPositive();

        mvc.perform(post("/api/drafts/" + id + "/retry").session(login()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":" + version(id) + "}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SCHEDULED"));
        publisher.runOnce();
        assertThat(postStatus(id)).isEqualTo("PUBLISHED");
    }

    @Test @Order(11)
    @DisplayName("Negative #12: after a crash, a post caught mid-send is never resent; one not yet sent is")
    void restartRecovery() throws Exception {
        UUID midSend = newDraft("Crash during send.");
        approve(midSend, "\"publishNow\":true").andExpect(status().isOk());
        jdbc.update("update publishing_queue set status='LOCKED', locked_until = now() - interval '1 minute' where post_id=?", midSend);
        jdbc.update("update generated_posts set status='PUBLISHING' where id=?", midSend);

        UUID beforeSend = newDraft("Crash before send.");
        approve(beforeSend, "\"publishNow\":true").andExpect(status().isOk());
        jdbc.update("update publishing_queue set status='LOCKED', locked_until = now() - interval '1 minute' where post_id=?", beforeSend);

        long before = X.count("/2/tweets");
        publisher.runOnce();
        assertThat(postStatus(midSend)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select last_error from publishing_queue where post_id=?", String.class, midSend))
                .contains("Check your X profile");
        assertThat(postStatus(beforeSend)).isEqualTo("PUBLISHED");
        assertThat(X.count("/2/tweets")).isEqualTo(before + 1);
    }

    @Test @Order(12)
    @DisplayName("If approval isn't acted on in time, the post is skipped and returns to review")
    void missedWindow() throws Exception {
        UUID id = newDraft("Missed window check post.");
        approve(id, "\"publishAt\":\"" + Instant.now().plusSeconds(300) + "\"").andExpect(status().isOk());
        jdbc.update("update publishing_queue set scheduled_at = now() - interval '2 hours' where post_id=?", id);
        long before = X.count("/2/tweets");
        publisher.runOnce();
        assertThat(X.count("/2/tweets")).isEqualTo(before);
        assertThat(queueStatus(id)).isEqualTo("SKIPPED");
        assertThat(postStatus(id)).isEqualTo("UNDER_REVIEW");
    }

    @Test @Order(13)
    @DisplayName("Approval input checks: past times, too far ahead, warnings, links")
    void approvalValidation() throws Exception {
        UUID id = newDraft("Validation check post https://www.tn.gov.in/pr/1");
        approve(id, "\"publishAt\":\"" + Instant.now().minusSeconds(60) + "\",\"acknowledgeWarnings\":true").andExpect(status().isBadRequest());
        approve(id, "\"publishAt\":\"" + Instant.now().plusSeconds(40L * 86400) + "\",\"acknowledgeWarnings\":true").andExpect(status().isBadRequest());
        approve(id, "\"publishNow\":true").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("link")));
        mvc.perform(post("/api/drafts/" + id + "/approve").session(login())
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"publishNow\":true}"))
                .andExpect(status().isForbidden()); // no CSRF
    }

    @Test @Order(14)
    @DisplayName("Refresh refused by X: account marked expired, post fails, user is notified")
    void refreshFailure() throws Exception {
        UUID id = newDraft("Refresh failure check post.");
        approve(id, "\"publishNow\":true").andExpect(status().isOk());
        X.refreshReply = new StubXServer.Reply(400, "{\"error\":\"invalid_grant\",\"error_description\":\"Value passed for the token was invalid.\"}", Map.of());
        X.tweetReplies.add(new StubXServer.Reply(401, "{\"title\":\"Unauthorized\"}", Map.of()));
        try {
            publisher.runOnce();
        } finally {
            X.refreshReply = null;
        }
        assertThat(postStatus(id)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select status from connected_accounts where provider_user_id='777'", String.class)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("select count(*) from notification_logs where type='OAUTH_EXPIRING'", Long.class)).isPositive();
    }

    @Test @Order(15)
    @DisplayName("Disconnect revokes tokens at X and deletes them locally")
    void disconnect() throws Exception {
        String state = connect(); // reconnect after expiry
        mvc.perform(get("/api/x/callback").session(login()).param("state", state).param("code", "again"))
                .andExpect(redirectedUrl("/settings?x=connected"));
        long revokes = X.count("/2/oauth2/revoke");
        mvc.perform(post("/api/x/disconnect").session(login()).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(false));
        assertThat(X.count("/2/oauth2/revoke")).isEqualTo(revokes + 2);
        Map<String, Object> row = jdbc.queryForMap("select status, access_token_encrypted, refresh_token_encrypted from connected_accounts where provider_user_id='777'");
        assertThat(row.get("status")).isEqualTo("REVOKED");
        assertThat(row.get("access_token_encrypted")).isNull();
        assertThat(row.get("refresh_token_encrypted")).isNull();

        // History shows who approved and published
        UUID published = jdbc.queryForObject("select pp.post_id from published_posts pp join generated_posts g on g.id = pp.post_id "
                + "where g.content like 'Chennai suburban bus services%'", UUID.class);
        mvc.perform(get("/api/drafts/" + published + "/history").session(login()))
                .andExpect(jsonPath("$[*].action").value(org.hamcrest.Matchers.hasItems("DRAFT_APPROVED", "POST_PUBLISHED")));
        setPublishing(false); // leave shared DB as other tests expect
    }
}
