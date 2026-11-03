package com.ai.xbot.entity;

import com.ai.xbot.entity.enums.ClaimType;
import com.ai.xbot.entity.enums.VerificationStatus;
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
 * A factual claim extracted from an article. supporting/conflicting URLs only ever hold URLs of
 * articles stored in news_articles — never URLs produced by the AI.
 */
@Entity
@Table(name = "factual_claims")
public class FactualClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "article_id", nullable = false)
    private UUID articleId;

    @Column(name = "claim_text", nullable = false, columnDefinition = "text")
    private String claimText;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type", nullable = false, length = 30)
    private ClaimType claimType;

    @Column(name = "attributed_to", length = 200)
    private String attributedTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 30)
    private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "supporting_urls", columnDefinition = "text[]", nullable = false)
    private List<String> supportingUrls = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "conflicting_urls", columnDefinition = "text[]", nullable = false)
    private List<String> conflictingUrls = new ArrayList<>();

    @Column(name = "verification_notes", columnDefinition = "text")
    private String verificationNotes;

    @Column(name = "verification_method", length = 40)
    private String verificationMethod;

    @Column(name = "independent_source_count", nullable = false)
    private int independentSourceCount;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Version
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FactualClaim() {}

    public FactualClaim(UUID articleId, String claimText, ClaimType claimType, String attributedTo) {
        this.articleId = articleId;
        this.claimText = claimText;
        this.claimType = claimType;
        this.attributedTo = attributedTo == null || attributedTo.isBlank() ? null
                : (attributedTo.length() > 200 ? attributedTo.substring(0, 200) : attributedTo);
    }

    public void recordVerification(VerificationStatus status, String method, int independentSources,
                                   List<String> supporting, List<String> conflicting, String notes, Instant at) {
        this.verificationStatus = status;
        this.verificationMethod = method;
        this.independentSourceCount = independentSources;
        this.supportingUrls = new ArrayList<>(supporting);
        this.conflictingUrls = new ArrayList<>(conflicting);
        this.verificationNotes = notes;
        this.verifiedAt = at;
    }

    public UUID getId() { return id; }
    public UUID getArticleId() { return articleId; }
    public String getClaimText() { return claimText; }
    public ClaimType getClaimType() { return claimType; }
    public String getAttributedTo() { return attributedTo; }
    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public List<String> getSupportingUrls() { return supportingUrls; }
    public List<String> getConflictingUrls() { return conflictingUrls; }
    public String getVerificationNotes() { return verificationNotes; }
    public String getVerificationMethod() { return verificationMethod; }
    public int getIndependentSourceCount() { return independentSourceCount; }
    public Instant getVerifiedAt() { return verifiedAt; }
}
