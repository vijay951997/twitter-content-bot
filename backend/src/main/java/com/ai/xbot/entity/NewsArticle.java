package com.ai.xbot.entity;

import com.ai.xbot.entity.enums.AnalysisStatus;
import com.ai.xbot.entity.enums.ContentLanguage;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * A discovered article. Stores metadata and an original AI-written summary only — never the
 * publisher's article text.
 */
@Entity
@Table(name = "news_articles")
public class NewsArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "original_url", nullable = false, unique = true, length = 1000)
    private String originalUrl;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "retrieved_at", nullable = false)
    private Instant retrievedAt;

    /** Original summary written by the AI in its own words (English, for cross-language matching). */
    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "topic_category", length = 40)
    private String topicCategory;

    @Column(length = 10)
    private ContentLanguage language;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", nullable = false, length = 20)
    private AnalysisStatus analysisStatus = AnalysisStatus.NEW;

    @Column(nullable = false)
    private boolean relevant = true;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "matched_keywords", columnDefinition = "text[]", nullable = false)
    private List<String> matchedKeywords = new ArrayList<>();

    @Column(name = "analysed_at")
    private Instant analysedAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "analysis_error", length = 500)
    private String analysisError;

    @Column(name = "focus_match", nullable = false)
    private boolean focusMatch;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NewsArticle() {}

    public NewsArticle(UUID sourceId, String title, String originalUrl, Instant publishedAt, Instant retrievedAt,
                       ContentLanguage language, String contentHash, boolean relevant, List<String> matchedKeywords,
                       String topicCategory) {
        this.sourceId = sourceId;
        this.title = title.length() > 500 ? title.substring(0, 500) : title;
        this.originalUrl = originalUrl;
        this.publishedAt = publishedAt;
        this.retrievedAt = retrievedAt;
        this.language = language;
        this.contentHash = contentHash;
        this.relevant = relevant;
        this.matchedKeywords = new ArrayList<>(matchedKeywords);
        this.topicCategory = topicCategory;
        this.analysisStatus = relevant ? AnalysisStatus.NEW : AnalysisStatus.IRRELEVANT;
    }

    public void recordAnalysis(String summary, String topicCategory, Instant at) {
        this.summary = summary;
        if (topicCategory != null) this.topicCategory = topicCategory;
        this.analysisStatus = AnalysisStatus.ANALYSED;
        this.analysedAt = at;
        this.analysisError = null;
    }

    public void recordAnalysisFailure(String error) {
        this.analysisStatus = AnalysisStatus.FAILED;
        this.analysisError = error == null ? null : (error.length() > 500 ? error.substring(0, 500) : error);
    }

    public void markVerified(Instant at) { this.verifiedAt = at; }

    public void setFocusMatch(boolean focusMatch) { this.focusMatch = focusMatch; }
    public boolean isFocusMatch() { return focusMatch; }

    public UUID getId() { return id; }
    public UUID getSourceId() { return sourceId; }
    public String getTitle() { return title; }
    public String getOriginalUrl() { return originalUrl; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getRetrievedAt() { return retrievedAt; }
    public String getSummary() { return summary; }
    public String getTopicCategory() { return topicCategory; }
    public ContentLanguage getLanguage() { return language; }
    public AnalysisStatus getAnalysisStatus() { return analysisStatus; }
    public boolean isRelevant() { return relevant; }
    public List<String> getMatchedKeywords() { return matchedKeywords; }
    public Instant getAnalysedAt() { return analysedAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public String getAnalysisError() { return analysisError; }
    /** Publication time, or retrieval time when the feed gave none. */
    public Instant effectiveTime() { return publishedAt != null ? publishedAt : retrievedAt; }
}
