-- =====================================================================
-- V1: Initial schema for the X content bot.
-- All 14 tables are created up front so later phases only add code.
-- Generated, approved and published content are kept in separate tables
-- so the approval chain is always traceable.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;  -- gen_random_uuid()

-- ---------------------------------------------------------------------
-- 1. users
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username        VARCHAR(64)  NOT NULL UNIQUE,
    password_hash   VARCHAR(100) NOT NULL,
    display_name    VARCHAR(120),
    role            VARCHAR(20)  NOT NULL DEFAULT 'ADMIN'
                    CHECK (role IN ('ADMIN', 'REVIEWER', 'VIEWER')),
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMPTZ,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- 2. connected_accounts  (X OAuth tokens, encrypted at rest - Phase 5)
-- ---------------------------------------------------------------------
CREATE TABLE connected_accounts (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                   UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider                  VARCHAR(20)  NOT NULL DEFAULT 'X' CHECK (provider IN ('X')),
    provider_user_id          VARCHAR(64)  NOT NULL,
    provider_username         VARCHAR(64),
    access_token_encrypted    TEXT         NOT NULL,
    refresh_token_encrypted   TEXT,
    token_expires_at          TIMESTAMPTZ,
    scopes                    VARCHAR(500),
    status                    VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                              CHECK (status IN ('ACTIVE', 'EXPIRED', 'REVOKED')),
    version                   BIGINT       NOT NULL DEFAULT 0,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_user_id)
);
CREATE INDEX idx_connected_accounts_user ON connected_accounts(user_id);

-- ---------------------------------------------------------------------
-- 3. news_sources  (configurable source registry)
-- ---------------------------------------------------------------------
CREATE TABLE news_sources (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(150) NOT NULL,
    base_url            VARCHAR(500) NOT NULL UNIQUE,
    feed_url            VARCHAR(500),
    source_type         VARCHAR(30)  NOT NULL
                        CHECK (source_type IN ('TAMIL_NEWS', 'NATIONAL_NEWS', 'GOVERNMENT',
                                               'PRESS_RELEASE', 'ELECTION_COMMISSION',
                                               'PARTY_OFFICIAL', 'LEGISLATIVE')),
    language            VARCHAR(10)  NOT NULL DEFAULT 'en' CHECK (language IN ('ta', 'en', 'mixed')),
    is_primary_source   BOOLEAN      NOT NULL DEFAULT FALSE,
    reliability_tier    SMALLINT     NOT NULL DEFAULT 2 CHECK (reliability_tier BETWEEN 1 AND 3),
    reliability_notes   TEXT,
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- 4. news_articles
-- ---------------------------------------------------------------------
CREATE TABLE news_articles (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id         UUID          NOT NULL REFERENCES news_sources(id),
    title             VARCHAR(500)  NOT NULL,
    original_url      VARCHAR(1000) NOT NULL UNIQUE,
    published_at      TIMESTAMPTZ,
    retrieved_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    summary           TEXT,          -- original summary, never the article body
    topic_category    VARCHAR(40),
    language          VARCHAR(10)   CHECK (language IN ('ta', 'en', 'mixed')),
    content_hash      VARCHAR(64),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_news_articles_source       ON news_articles(source_id);
CREATE INDEX idx_news_articles_published_at ON news_articles(published_at DESC);
CREATE INDEX idx_news_articles_category     ON news_articles(topic_category);

-- ---------------------------------------------------------------------
-- 5. factual_claims
-- ---------------------------------------------------------------------
CREATE TABLE factual_claims (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    article_id           UUID         NOT NULL REFERENCES news_articles(id) ON DELETE CASCADE,
    claim_text           TEXT         NOT NULL,
    claim_type           VARCHAR(30)  NOT NULL DEFAULT 'FACT'
                         CHECK (claim_type IN ('FACT', 'ATTRIBUTED_STATEMENT', 'STATISTIC', 'OPINION')),
    attributed_to        VARCHAR(200),
    verification_status  VARCHAR(30)  NOT NULL DEFAULT 'UNVERIFIED'
                         CHECK (verification_status IN ('VERIFIED', 'PARTIALLY_VERIFIED',
                                                        'UNVERIFIED', 'CONFLICTING_INFORMATION')),
    supporting_urls      TEXT[]       NOT NULL DEFAULT '{}',
    conflicting_urls     TEXT[]       NOT NULL DEFAULT '{}',
    verification_notes   TEXT,
    verified_at          TIMESTAMPTZ,
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_factual_claims_article ON factual_claims(article_id);
CREATE INDEX idx_factual_claims_status  ON factual_claims(verification_status);

-- ---------------------------------------------------------------------
-- 6. generated_posts  (AI drafts; never published directly)
-- ---------------------------------------------------------------------
CREATE TABLE generated_posts (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content                 TEXT         NOT NULL,
    content_hash            VARCHAR(64)  NOT NULL,   -- SHA-256 of normalized content
    language                VARCHAR(10)  NOT NULL CHECK (language IN ('ta', 'en', 'mixed')),
    category                VARCHAR(40)  NOT NULL,
    slot                    VARCHAR(20)  CHECK (slot IN ('MORNING', 'AFTERNOON', 'EVENING')),
    status                  VARCHAR(20)  NOT NULL DEFAULT 'GENERATED'
                            CHECK (status IN ('GENERATED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED',
                                              'SCHEDULED', 'PUBLISHING', 'PUBLISHED', 'FAILED',
                                              'CANCELLED')),
    verification_status     VARCHAR(30)  NOT NULL DEFAULT 'UNVERIFIED'
                            CHECK (verification_status IN ('VERIFIED', 'PARTIALLY_VERIFIED',
                                                           'UNVERIFIED', 'CONFLICTING_INFORMATION')),
    quality_estimate        NUMERIC(4,3) CHECK (quality_estimate BETWEEN 0 AND 1), -- an estimate only
    proposed_publish_at     TIMESTAMPTZ,
    variation_group_id      UUID,        -- variations of the same topic share this id
    parent_post_id          UUID         REFERENCES generated_posts(id), -- set on regenerate
    ai_model                VARCHAR(80),
    prompt_version          VARCHAR(40),
    raw_ai_response         JSONB,
    saved_for_later         BOOLEAN      NOT NULL DEFAULT FALSE,
    version                 BIGINT       NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_generated_posts_status       ON generated_posts(status);
CREATE INDEX idx_generated_posts_created_at   ON generated_posts(created_at DESC);
CREATE INDEX idx_generated_posts_content_hash ON generated_posts(content_hash);
CREATE INDEX idx_generated_posts_publish_at   ON generated_posts(proposed_publish_at);

-- ---------------------------------------------------------------------
-- 7. post_sources  (draft <-> article / claim references)
-- ---------------------------------------------------------------------
CREATE TABLE post_sources (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id       UUID          NOT NULL REFERENCES generated_posts(id) ON DELETE CASCADE,
    article_id    UUID          REFERENCES news_articles(id),
    claim_id      UUID          REFERENCES factual_claims(id),
    source_url    VARCHAR(1000) NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_post_sources_post    ON post_sources(post_id);
CREATE INDEX idx_post_sources_article ON post_sources(article_id);

-- ---------------------------------------------------------------------
-- 8. post_approvals
-- Approval is bound to the exact content via content_hash.
-- Editing a post makes the hash differ, so the old approval no longer
-- matches and is marked invalidated.
-- ---------------------------------------------------------------------
CREATE TABLE post_approvals (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id               UUID         NOT NULL REFERENCES generated_posts(id) ON DELETE CASCADE,
    decision              VARCHAR(20)  NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
    decided_by            UUID         NOT NULL REFERENCES users(id),
    decided_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    approved_content_hash VARCHAR(64)  NOT NULL,
    comment               TEXT,
    invalidated_at        TIMESTAMPTZ,
    invalidation_reason   VARCHAR(200),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_post_approvals_post ON post_approvals(post_id);
-- At most one live (non-invalidated) approval per post
CREATE UNIQUE INDEX uq_post_approvals_active
    ON post_approvals(post_id) WHERE invalidated_at IS NULL AND decision = 'APPROVED';

-- ---------------------------------------------------------------------
-- 9. publishing_queue
-- A queue row cannot exist without an approval (approval_id NOT NULL).
-- idempotency_key is unique, so the same post cannot be queued twice.
-- ---------------------------------------------------------------------
CREATE TABLE publishing_queue (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id           UUID         NOT NULL REFERENCES generated_posts(id),
    approval_id       UUID         NOT NULL REFERENCES post_approvals(id),
    scheduled_at      TIMESTAMPTZ  NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING', 'LOCKED', 'DONE', 'FAILED', 'CANCELLED', 'SKIPPED')),
    idempotency_key   VARCHAR(100) NOT NULL UNIQUE,
    attempt_count     INT          NOT NULL DEFAULT 0,
    next_attempt_at   TIMESTAMPTZ,
    last_error        TEXT,
    locked_by         VARCHAR(100),
    locked_until      TIMESTAMPTZ,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_publishing_queue_due ON publishing_queue(status, scheduled_at);
-- Only one active queue entry per post
CREATE UNIQUE INDEX uq_publishing_queue_active_post
    ON publishing_queue(post_id) WHERE status IN ('PENDING', 'LOCKED');

-- ---------------------------------------------------------------------
-- 10. published_posts
-- UNIQUE(post_id) is the final guard against double publishing.
-- ---------------------------------------------------------------------
CREATE TABLE published_posts (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id               UUID         NOT NULL UNIQUE REFERENCES generated_posts(id),
    approval_id           UUID         NOT NULL REFERENCES post_approvals(id),
    connected_account_id  UUID         NOT NULL REFERENCES connected_accounts(id),
    x_post_id             VARCHAR(64)  NOT NULL UNIQUE,
    published_content     TEXT         NOT NULL,   -- exact text sent to X
    published_at          TIMESTAMPTZ  NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_published_posts_published_at ON published_posts(published_at DESC);

-- ---------------------------------------------------------------------
-- 11. post_metrics  (only values actually returned by X API)
-- ---------------------------------------------------------------------
CREATE TABLE post_metrics (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    published_post_id   UUID         NOT NULL REFERENCES published_posts(id) ON DELETE CASCADE,
    captured_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    impressions         BIGINT,      -- NULL = not returned by API (never zero-filled)
    likes               BIGINT,
    reposts             BIGINT,
    replies             BIGINT,
    quotes              BIGINT,
    bookmarks           BIGINT,
    raw_metrics         JSONB
);
CREATE INDEX idx_post_metrics_post_time ON post_metrics(published_post_id, captured_at DESC);

-- ---------------------------------------------------------------------
-- 12. audit_logs  (append-only)
-- ---------------------------------------------------------------------
CREATE TABLE audit_logs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id      UUID         REFERENCES users(id),
    actor_type    VARCHAR(20)  NOT NULL DEFAULT 'USER' CHECK (actor_type IN ('USER', 'SYSTEM', 'SCHEDULER')),
    action        VARCHAR(60)  NOT NULL,
    entity_type   VARCHAR(60)  NOT NULL,
    entity_id     UUID,
    old_value     JSONB,
    new_value     JSONB,
    details       TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_logs_entity  ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_created ON audit_logs(created_at DESC);

-- Block UPDATE/DELETE on audit_logs so history cannot be rewritten
CREATE OR REPLACE FUNCTION audit_logs_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_logs_immutable
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_immutable();

-- ---------------------------------------------------------------------
-- 13. application_settings  (key/value, editable from dashboard)
-- ---------------------------------------------------------------------
CREATE TABLE application_settings (
    setting_key    VARCHAR(100) PRIMARY KEY,
    setting_value  TEXT         NOT NULL,
    description    VARCHAR(300),
    updated_by     UUID         REFERENCES users(id),
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- 14. notification_logs
-- ---------------------------------------------------------------------
CREATE TABLE notification_logs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         REFERENCES users(id) ON DELETE CASCADE,
    type          VARCHAR(40)  NOT NULL
                  CHECK (type IN ('DRAFTS_GENERATED', 'DRAFT_PENDING', 'UPCOMING_POST',
                                  'PUBLISH_SUCCESS', 'PUBLISH_FAILURE', 'OAUTH_EXPIRING',
                                  'VERIFICATION_ISSUE', 'SYSTEM')),
    channel       VARCHAR(20)  NOT NULL DEFAULT 'DASHBOARD'
                  CHECK (channel IN ('DASHBOARD', 'EMAIL', 'TELEGRAM')),
    title         VARCHAR(200) NOT NULL,
    message       TEXT,
    related_entity_type VARCHAR(60),
    related_entity_id   UUID,
    read_at       TIMESTAMPTZ,
    delivered_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_logs_user_unread ON notification_logs(user_id, read_at);

-- ---------------------------------------------------------------------
-- Default settings.
-- Initial values come from environment variables via Flyway placeholders
-- (see spring.flyway.placeholders in application.yml). After the first
-- start, the dashboard is the source of truth for these settings.
-- ---------------------------------------------------------------------
INSERT INTO application_settings (setting_key, setting_value, description) VALUES
    ('schedule.timezone',         '${app_timezone}',      'Timezone used for all schedules'),
    ('schedule.morning',          '${schedule_morning}',  'Morning slot: governance, announcements, infrastructure, policy'),
    ('schedule.afternoon',        '${schedule_afternoon}','Afternoon slot: TN politics, TVK announcements, statements, legislature'),
    ('schedule.evening',          '${schedule_evening}',  'Evening slot: civic accountability, public interest, analysis'),
    ('content.enabled_languages', '${enabled_languages}', 'Comma-separated: ta, en, mixed'),
    ('content.variations_per_topic', '2',                 'Variations generated per topic'),
    ('duplicate.lookback_days',   '30',                   'Days of history compared for duplicates'),
    ('publishing.enabled',        'false',                'Master switch. Even when true, only APPROVED posts publish.');

-- Equal split across enabled languages; the last language takes the remainder so the total is 100.
INSERT INTO application_settings (setting_key, setting_value, description)
SELECT 'content.language_mix',
       string_agg(lang || ':' || CASE WHEN rn = n THEN 100 - (100 / n) * (n - 1) ELSE 100 / n END, ',' ORDER BY rn),
       'Preferred language distribution in percent'
FROM (
    SELECT trim(l) AS lang, row_number() OVER () AS rn, count(*) OVER () AS n
    FROM unnest(string_to_array('${enabled_languages}', ',')) AS l
) t;
