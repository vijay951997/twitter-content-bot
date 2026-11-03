-- =====================================================================
-- V4: Phase 4 (approval workflow) and Phase 5 (X publishing).
-- =====================================================================

ALTER TABLE generated_posts
    ADD COLUMN rejection_reason VARCHAR(500),
    ADD COLUMN edited_by_human  BOOLEAN NOT NULL DEFAULT FALSE;

-- Revoked accounts keep their row (published posts reference it) but no tokens.
ALTER TABLE connected_accounts ALTER COLUMN access_token_encrypted DROP NOT NULL;

INSERT INTO application_settings (setting_key, setting_value, description) VALUES
    ('publishing.missed_window_minutes', '60', 'A scheduled post more than this late is not published and goes back to review'),
    ('publishing.max_attempts',          '5',  'Attempts for temporary X API failures before a post is marked FAILED');
