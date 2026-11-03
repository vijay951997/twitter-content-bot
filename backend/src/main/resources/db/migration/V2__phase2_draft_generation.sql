-- =====================================================================
-- V2: Phase 2 - AI draft generation.
-- Adds the inputs and AI output details each draft needs for review.
-- =====================================================================

ALTER TABLE generated_posts
    ADD COLUMN topic             VARCHAR(300),
    ADD COLUMN source_notes      TEXT,           -- material the user supplied to ground the draft
    ADD COLUMN ai_claims         JSONB,          -- {"verified":[], "unverified":[]} as labelled by the AI (not independent verification)
    ADD COLUMN risk_flags        TEXT[]  NOT NULL DEFAULT '{}',
    ADD COLUMN duplicate_warning VARCHAR(300),
    ADD COLUMN weighted_length   INT;            -- X weighted character count

CREATE INDEX idx_generated_posts_variation_group ON generated_posts(variation_group_id);

INSERT INTO application_settings (setting_key, setting_value, description) VALUES
    ('ai.max_generations_per_hour', '30', 'Upper bound on AI generation requests per hour, to protect API spend');
