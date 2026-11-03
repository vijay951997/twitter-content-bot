-- =====================================================================
-- V5: Configurable content focus (voice, slot themes, hashtags, focus news).
-- Defaults reflect the account owner's stated focus: TVK, Chief Minister
-- C. Joseph Vijay and the TVK government's schemes. All editable in Settings.
-- =====================================================================
INSERT INTO application_settings (setting_key, setting_value, description) VALUES
    ('content.voice',
     'I am a TVK member and supporter. Write in a proud, warm, positive voice about TVK, Chief Minister C. Joseph Vijay and the TVK government: highlight new schemes, government orders, achievements and how they benefit people in Tamil Nadu. Be confident and respectful; never insult opponents.',
     'Who is speaking and from what perspective (used for drafting only, never for fact-checking)'),
    ('content.theme_morning',
     'New TVK government schemes, government orders and announcements, and how people benefit from them',
     'Morning slot theme'),
    ('content.theme_afternoon',
     'Chief Minister Vijay''s latest updates, visits, decisions and statements, and TVK party news',
     'Afternoon slot theme'),
    ('content.theme_evening',
     'Impact and benefits of TVK government programmes for ordinary people, and public-interest questions',
     'Evening slot theme'),
    ('content.hashtags', '#TVK, #CMVijay, #Vijay, #TVKGovernment, #TamilNadu, #தவெக',
     'Hashtags drafts may use, most relevant first'),
    ('content.max_hashtags', '3', 'Maximum hashtags per post'),
    ('news.focus_keywords',
     'tvk, vijay, tamilaga vettri kazhagam, chief minister, cm vijay, தவெக, விஜய், முதல்வர், முதலமைச்சர், தமிழக வெற்றிக் கழகம்',
     'News matching these is preferred for daily drafts'),
    ('news.focus_only', 'false', 'Keep only news that matches the focus keywords');

-- Focus-keyword match on articles (preferred for daily drafts)
ALTER TABLE news_articles ADD COLUMN focus_match BOOLEAN NOT NULL DEFAULT FALSE;

-- Official manual source: no feed. When a pasted source link is on this domain, its facts count as
-- coming from an official primary source.
INSERT INTO news_sources (name, base_url, feed_url, source_type, language, is_primary_source, reliability_tier,
                          reliability_notes, enabled, requires_keyword_match)
VALUES ('TN DIPR – Government press releases', 'https://www.dipr.tn.gov.in/', NULL, 'PRESS_RELEASE', 'ta', TRUE, 1,
        'Tamil Nadu Department of Information and Public Relations. No RSS feed found; paste releases in Drafts with their dipr.tn.gov.in link.',
        TRUE, FALSE)
ON CONFLICT (base_url) DO NOTHING;
