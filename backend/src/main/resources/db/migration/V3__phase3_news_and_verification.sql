-- =====================================================================
-- V3: Phase 3 - news discovery, source registry and claim verification.
-- =====================================================================

ALTER TABLE news_sources
    ADD COLUMN fetch_interval_minutes INT          NOT NULL DEFAULT 60 CHECK (fetch_interval_minutes >= 15),
    ADD COLUMN requires_keyword_match BOOLEAN      NOT NULL DEFAULT TRUE,  -- false for feeds that are already TN-specific
    ADD COLUMN last_fetched_at        TIMESTAMPTZ,
    ADD COLUMN last_fetch_status      VARCHAR(40),
    ADD COLUMN last_fetch_message     VARCHAR(500),
    ADD COLUMN consecutive_failures   INT          NOT NULL DEFAULT 0,
    ADD COLUMN etag                   VARCHAR(300),
    ADD COLUMN last_modified          VARCHAR(100);

ALTER TABLE news_articles
    ADD COLUMN analysis_status   VARCHAR(20) NOT NULL DEFAULT 'NEW'
                                 CHECK (analysis_status IN ('NEW', 'ANALYSED', 'FAILED', 'IRRELEVANT')),
    ADD COLUMN relevant          BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN matched_keywords  TEXT[]      NOT NULL DEFAULT '{}',
    ADD COLUMN analysed_at       TIMESTAMPTZ,
    ADD COLUMN verified_at       TIMESTAMPTZ,
    ADD COLUMN analysis_error    VARCHAR(500);

CREATE INDEX idx_news_articles_status ON news_articles(analysis_status, relevant);

ALTER TABLE factual_claims
    ADD COLUMN verification_method      VARCHAR(40),
    ADD COLUMN independent_source_count INT NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------
-- Starter sources. Feed URLs were checked against public feed listings
-- when this migration was written; the dashboard shows fetch errors if a
-- publisher changes a URL. Add Tamil-language and official sources from the
-- dashboard. reliability_tier: 1 = official primary source, 2 = established
-- news organisation, 3 = other.
-- ---------------------------------------------------------------------
INSERT INTO news_sources (name, base_url, feed_url, source_type, language, is_primary_source, reliability_tier,
                          reliability_notes, enabled, requires_keyword_match) VALUES
    ('The Hindu – Tamil Nadu', 'https://www.thehindu.com/news/national/tamil-nadu/',
     'https://www.thehindu.com/news/national/tamil-nadu/feeder/default.rss',
     'NATIONAL_NEWS', 'en', FALSE, 2, 'National newspaper, Tamil Nadu section', TRUE, FALSE),
    ('The Hindu – Chennai', 'https://www.thehindu.com/news/cities/chennai/',
     'https://www.thehindu.com/news/cities/chennai/feeder/default.rss',
     'NATIONAL_NEWS', 'en', FALSE, 2, 'National newspaper, Chennai city section', TRUE, FALSE),
    ('BBC Tamil', 'https://www.bbc.com/tamil',
     'https://feeds.bbci.co.uk/tamil/rss.xml',
     'TAMIL_NEWS', 'ta', FALSE, 2, 'Tamil-language service of the BBC; covers world news too, so keyword filtering is on', TRUE, TRUE),
    ('PIB – Press releases (English)', 'https://www.pib.gov.in/',
     'https://www.pib.gov.in/RssMain.aspx?ModId=6&Lang=1&Regid=3',
     'PRESS_RELEASE', 'en', TRUE, 1, 'Government of India press releases. Disabled by default: enable and use Test to confirm the English feed URL', FALSE, TRUE);

INSERT INTO application_settings (setting_key, setting_value, description) VALUES
    ('news.auto_fetch_enabled',     'false', 'Fetch enabled sources automatically on their interval'),
    ('news.auto_analyse_enabled',   'false', 'Analyse new relevant articles with AI automatically (uses API credits)'),
    ('news.max_auto_analyses_per_run', '5',  'Upper bound on automatic AI analyses per fetch run'),
    ('ai.max_analyses_per_hour',    '60',    'Upper bound on AI article analysis + verification calls per hour'),
    ('news.verification_window_hours', '72', 'How far apart two reports can be and still corroborate each other');
