package com.ai.xbot.entity;

import com.ai.xbot.entity.enums.ContentLanguage;
import com.ai.xbot.entity.enums.PostSlot;
import com.ai.xbot.entity.enums.PostStatus;
import com.ai.xbot.entity.enums.VerificationStatus;
import com.ai.xbot.service.workflow.PostStatusPolicy;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * An AI-generated draft. A draft is never published directly: it must pass through
 * an explicit approval (post_approvals) and the publishing queue.
 * Status changes are only possible through {@link #transitionTo(PostStatus)}, which enforces
 * {@link PostStatusPolicy}.
 */
@Entity
@Table(name = "generated_posts")
public class GeneratedPost {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(nullable = false, length = 10)
    private ContentLanguage language;

    @Column(nullable = false, length = 40)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PostSlot slot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status = PostStatus.GENERATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 30)
    private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;

    @Column(name = "quality_estimate", precision = 4, scale = 3)
    private BigDecimal qualityEstimate;

    @Column(name = "proposed_publish_at")
    private Instant proposedPublishAt;

    @Column(name = "saved_for_later", nullable = false)
    private boolean savedForLater;

    @Column(name = "variation_group_id")
    private UUID variationGroupId;

    @Column(name = "parent_post_id")
    private UUID parentPostId;

    @Column(name = "ai_model", length = 80)
    private String aiModel;

    @Column(name = "prompt_version", length = 40)
    private String promptVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_ai_response", columnDefinition = "jsonb")
    private String rawAiResponse;

    @Column(length = 300)
    private String topic;

    @Column(name = "source_notes", columnDefinition = "text")
    private String sourceNotes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_claims", columnDefinition = "jsonb")
    private String aiClaims;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "risk_flags", columnDefinition = "text[]", nullable = false)
    private List<String> riskFlags = new ArrayList<>();

    @Column(name = "duplicate_warning", length = 300)
    private String duplicateWarning;

    @Column(name = "weighted_length")
    private Integer weightedLength;

    @Version
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GeneratedPost() {}

    public GeneratedPost(String content, String contentHash, ContentLanguage language,
                         String category, PostSlot slot, Instant proposedPublishAt) {
        this.content = content;
        this.contentHash = contentHash;
        this.language = language;
        this.category = category;
        this.slot = slot;
        this.proposedPublishAt = proposedPublishAt;
    }

    /** Records what produced this draft. Called once, before the draft is first saved. */
    public void recordGeneration(UUID variationGroupId, String aiModel, String promptVersion, String rawAiResponse,
                                 String topic, String sourceNotes) {
        this.variationGroupId = variationGroupId;
        this.aiModel = aiModel;
        this.promptVersion = promptVersion;
        this.rawAiResponse = rawAiResponse;
        this.topic = topic;
        this.sourceNotes = sourceNotes;
    }

    /** Stores the review aids derived from the AI output and local checks. */
    public void recordReviewAids(VerificationStatus verificationStatus, BigDecimal qualityEstimate, String aiClaims,
                                 List<String> riskFlags, String duplicateWarning, int weightedLength) {
        this.verificationStatus = verificationStatus;
        this.qualityEstimate = qualityEstimate;
        this.aiClaims = aiClaims;
        this.riskFlags = new ArrayList<>(riskFlags);
        this.duplicateWarning = duplicateWarning;
        this.weightedLength = weightedLength;
    }

    /** Moves the draft to a new status, rejecting any transition the workflow does not allow. */
    public void transitionTo(PostStatus target) {
        PostStatusPolicy.assertTransition(this.status, target);
        this.status = target;
    }

    public UUID getId() { return id; }
    public String getContent() { return content; }
    public String getContentHash() { return contentHash; }
    public ContentLanguage getLanguage() { return language; }
    public String getCategory() { return category; }
    public PostSlot getSlot() { return slot; }
    public PostStatus getStatus() { return status; }
    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public BigDecimal getQualityEstimate() { return qualityEstimate; }
    public Instant getProposedPublishAt() { return proposedPublishAt; }
    public boolean isSavedForLater() { return savedForLater; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getVariationGroupId() { return variationGroupId; }
    public UUID getParentPostId() { return parentPostId; }
    public String getAiModel() { return aiModel; }
    public String getPromptVersion() { return promptVersion; }
    public String getTopic() { return topic; }
    public String getSourceNotes() { return sourceNotes; }
    public String getAiClaims() { return aiClaims; }
    public List<String> getRiskFlags() { return riskFlags; }
    public String getDuplicateWarning() { return duplicateWarning; }
    public Integer getWeightedLength() { return weightedLength; }
    public long getVersion() { return version; }
}
