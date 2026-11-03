package com.ai.xbot.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies the Flyway schema and the database-level safety nets that back the
 * "never publish without approval" and "never publish twice" rules.
 */
class SchemaMigrationIT extends AbstractPostgresIT {

    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("All 14 tables exist")
    void allTablesCreated() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema='public' "
                        + "and table_name <> 'flyway_schema_history'", String.class);
        assertThat(tables).containsExactlyInAnyOrder(
                "users", "connected_accounts", "news_sources", "news_articles", "factual_claims",
                "generated_posts", "post_sources", "post_approvals", "publishing_queue",
                "published_posts", "post_metrics", "audit_logs", "application_settings",
                "notification_logs");
    }

    @Test
    @DisplayName("Default settings are seeded and publishing is off by default")
    void defaultSettings() {
        assertThat(jdbc.queryForObject(
                "select setting_value from application_settings where setting_key='schedule.timezone'",
                String.class)).isEqualTo("Asia/Kolkata");
        assertThat(jdbc.queryForObject(
                "select setting_value from application_settings where setting_key='publishing.enabled'",
                String.class)).isEqualTo("false");
        assertThat(jdbc.queryForObject(
                "select setting_value from application_settings where setting_key='content.language_mix'",
                String.class)).isEqualTo("ta:50,en:50");
    }

    @Test
    @DisplayName("A queue entry cannot be created without an approval")
    void queueRequiresApproval() {
        UUID postId = insertPost();
        assertThatThrownBy(() -> jdbc.update(
                "insert into publishing_queue (post_id, approval_id, scheduled_at, idempotency_key) "
                        + "values (?, null, now(), ?)", postId, "k-" + postId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("A post can be recorded as published only once")
    void publishedPostIsUnique() {
        UUID userId = jdbc.queryForObject("select id from users limit 1", UUID.class);
        UUID postId = insertPost();
        UUID approvalId = jdbc.queryForObject(
                "insert into post_approvals (post_id, decision, decided_by, approved_content_hash) "
                        + "values (?, 'APPROVED', ?, 'h') returning id", UUID.class, postId, userId);
        UUID accountId = jdbc.queryForObject(
                "insert into connected_accounts (user_id, provider_user_id, access_token_encrypted) "
                        + "values (?, ?, 'enc') returning id", UUID.class, userId, "x-" + postId);

        jdbc.update("insert into published_posts (post_id, approval_id, connected_account_id, x_post_id, "
                + "published_content, published_at) values (?, ?, ?, ?, 'c', now())",
                postId, approvalId, accountId, "x1-" + postId);

        assertThatThrownBy(() -> jdbc.update("insert into published_posts (post_id, approval_id, "
                + "connected_account_id, x_post_id, published_content, published_at) values (?, ?, ?, ?, 'c', now())",
                postId, approvalId, accountId, "x2-" + postId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Only one live approval per post")
    void singleActiveApproval() {
        UUID userId = jdbc.queryForObject("select id from users limit 1", UUID.class);
        UUID postId = insertPost();
        jdbc.update("insert into post_approvals (post_id, decision, decided_by, approved_content_hash) "
                + "values (?, 'APPROVED', ?, 'h1')", postId, userId);
        assertThatThrownBy(() -> jdbc.update("insert into post_approvals (post_id, decision, decided_by, "
                + "approved_content_hash) values (?, 'APPROVED', ?, 'h2')", postId, userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Audit log is append-only")
    void auditLogIsImmutable() {
        UUID id = jdbc.queryForObject("insert into audit_logs (actor_type, action, entity_type) "
                + "values ('SYSTEM', 'TEST', 'test') returning id", UUID.class);
        assertThatThrownBy(() -> jdbc.update("update audit_logs set action='CHANGED' where id=?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from audit_logs where id=?", id))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("Invalid status values are rejected by the database")
    void statusCheckConstraint() {
        assertThatThrownBy(() -> jdbc.update("insert into generated_posts (content, content_hash, language, "
                + "category, status) values ('c', 'h', 'en', 'X', 'AUTO_PUBLISHED')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertPost() {
        return jdbc.queryForObject("insert into generated_posts (content, content_hash, language, category) "
                + "values ('test', 'hash', 'en', 'GOVERNANCE') returning id", UUID.class);
    }
}
