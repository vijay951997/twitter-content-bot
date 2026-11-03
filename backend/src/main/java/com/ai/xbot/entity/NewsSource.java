package com.ai.xbot.entity;

import com.ai.xbot.entity.enums.ContentLanguage;
import com.ai.xbot.entity.enums.SourceType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A news or official source in the registry. Only enabled sources are fetched. */
@Entity
@Table(name = "news_sources")
public class NewsSource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "base_url", nullable = false, unique = true, length = 500)
    private String baseUrl;

    @Column(name = "feed_url", length = 500)
    private String feedUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private SourceType sourceType;

    @Column(nullable = false, length = 10)
    private ContentLanguage language;

    @Column(name = "is_primary_source", nullable = false)
    private boolean primarySource;

    @Column(name = "reliability_tier", nullable = false)
    private short reliabilityTier;

    @Column(name = "reliability_notes", columnDefinition = "text")
    private String reliabilityNotes;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "fetch_interval_minutes", nullable = false)
    private int fetchIntervalMinutes = 60;

    @Column(name = "requires_keyword_match", nullable = false)
    private boolean requiresKeywordMatch = true;

    @Column(name = "last_fetched_at")
    private Instant lastFetchedAt;

    @Column(name = "last_fetch_status", length = 40)
    private String lastFetchStatus;

    @Column(name = "last_fetch_message", length = 500)
    private String lastFetchMessage;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(length = 300)
    private String etag;

    @Column(name = "last_modified", length = 100)
    private String lastModified;

    @Version
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NewsSource() {}

    public NewsSource(String name, String baseUrl, String feedUrl, SourceType sourceType, ContentLanguage language,
                      boolean primarySource, int reliabilityTier, String reliabilityNotes, boolean enabled,
                      int fetchIntervalMinutes, boolean requiresKeywordMatch) {
        update(name, baseUrl, feedUrl, sourceType, language, primarySource, reliabilityTier, reliabilityNotes, enabled,
                fetchIntervalMinutes, requiresKeywordMatch);
    }

    public void update(String name, String baseUrl, String feedUrl, SourceType sourceType, ContentLanguage language,
                       boolean primarySource, int reliabilityTier, String reliabilityNotes, boolean enabled,
                       int fetchIntervalMinutes, boolean requiresKeywordMatch) {
        if (this.feedUrl != null && !this.feedUrl.equals(feedUrl)) {
            this.etag = null;
            this.lastModified = null;
        }
        this.name = name;
        this.baseUrl = baseUrl;
        this.feedUrl = feedUrl;
        this.sourceType = sourceType;
        this.language = language;
        this.primarySource = primarySource;
        this.reliabilityTier = (short) reliabilityTier;
        this.reliabilityNotes = reliabilityNotes;
        this.enabled = enabled;
        this.fetchIntervalMinutes = fetchIntervalMinutes;
        this.requiresKeywordMatch = requiresKeywordMatch;
    }

    public void recordFetch(Instant at, String status, String message, boolean success, String etag, String lastModified) {
        this.lastFetchedAt = at;
        this.lastFetchStatus = status;
        this.lastFetchMessage = message == null ? null : (message.length() > 500 ? message.substring(0, 500) : message);
        this.consecutiveFailures = success ? 0 : consecutiveFailures + 1;
        if (success) {
            if (etag != null) this.etag = etag;
            if (lastModified != null) this.lastModified = lastModified;
        }
    }

    /** Due when enabled and the interval has passed; failing sources back off (×2 per failure, max 24h). */
    public boolean isDue(Instant now) {
        if (!enabled || feedUrl == null) return false;
        if (lastFetchedAt == null) return true;
        long minutes = (long) fetchIntervalMinutes << Math.min(consecutiveFailures, 6);
        return !lastFetchedAt.plusSeconds(Math.min(minutes, 1440) * 60).isAfter(now);
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getBaseUrl() { return baseUrl; }
    public String getFeedUrl() { return feedUrl; }
    public SourceType getSourceType() { return sourceType; }
    public ContentLanguage getLanguage() { return language; }
    public boolean isPrimarySource() { return primarySource; }
    public int getReliabilityTier() { return reliabilityTier; }
    public String getReliabilityNotes() { return reliabilityNotes; }
    public boolean isEnabled() { return enabled; }
    public int getFetchIntervalMinutes() { return fetchIntervalMinutes; }
    public boolean isRequiresKeywordMatch() { return requiresKeywordMatch; }
    public Instant getLastFetchedAt() { return lastFetchedAt; }
    public String getLastFetchStatus() { return lastFetchStatus; }
    public String getLastFetchMessage() { return lastFetchMessage; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public String getEtag() { return etag; }
    public String getLastModified() { return lastModified; }
}
