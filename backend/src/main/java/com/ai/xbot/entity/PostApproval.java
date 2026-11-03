package com.ai.xbot.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A human decision on a draft. An approval is bound to the exact content via its hash; any later edit
 * invalidates it. Approvals are only ever created by an authenticated user's explicit action.
 */
@Entity
@Table(name = "post_approvals")
public class PostApproval {

    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(nullable = false, length = 20)
    private String decision;

    @Column(name = "decided_by", nullable = false)
    private UUID decidedBy;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    @Column(name = "approved_content_hash", nullable = false, length = 64)
    private String approvedContentHash;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Column(name = "invalidation_reason", length = 200)
    private String invalidationReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PostApproval() {}

    public PostApproval(UUID postId, String decision, UUID decidedBy, Instant decidedAt, String contentHash, String comment) {
        if (decidedBy == null) throw new IllegalArgumentException("A decision must be made by a user");
        this.postId = postId;
        this.decision = decision;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
        this.approvedContentHash = contentHash;
        this.comment = comment;
    }

    public void invalidate(Instant at, String reason) {
        if (invalidatedAt == null) {
            this.invalidatedAt = at;
            this.invalidationReason = reason;
        }
    }

    public boolean isLiveApproval() { return APPROVED.equals(decision) && invalidatedAt == null; }

    public UUID getId() { return id; }
    public UUID getPostId() { return postId; }
    public String getDecision() { return decision; }
    public UUID getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getApprovedContentHash() { return approvedContentHash; }
    public String getComment() { return comment; }
    public Instant getInvalidatedAt() { return invalidatedAt; }
    public String getInvalidationReason() { return invalidationReason; }
}
