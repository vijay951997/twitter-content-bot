package com.ai.xbot.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** One scheduled publication. Cannot exist without an approval (DB: approval_id NOT NULL). */
@Entity
@Table(name = "publishing_queue")
public class PublishingQueueEntry {

    public enum Status { PENDING, LOCKED, DONE, FAILED, CANCELLED, SKIPPED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "approval_id", nullable = false)
    private UUID approvalId;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Version
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PublishingQueueEntry() {}

    public PublishingQueueEntry(UUID postId, UUID approvalId, Instant scheduledAt) {
        this.postId = postId;
        this.approvalId = approvalId;
        this.scheduledAt = scheduledAt;
        this.idempotencyKey = "post:" + postId + ":approval:" + approvalId;
    }

    public void lock(String worker, Instant until) {
        this.status = Status.LOCKED;
        this.lockedBy = worker;
        this.lockedUntil = until;
        this.attemptCount++;
    }

    public void done() { this.status = Status.DONE; this.lastError = null; release(); }
    public void fail(String error) { this.status = Status.FAILED; this.lastError = trim(error); release(); }
    public void skip(String reason) { this.status = Status.SKIPPED; this.lastError = trim(reason); release(); }
    public void cancel(String reason) { this.status = Status.CANCELLED; this.lastError = trim(reason); release(); }

    public void retryAt(Instant when, String error) {
        this.status = Status.PENDING;
        this.nextAttemptAt = when;
        this.lastError = trim(error);
        release();
    }

    /** Puts the entry back without counting an attempt (e.g. publishing switched off, no account yet). */
    public void deferWithoutAttempt(Instant when, String reason) {
        this.attemptCount = Math.max(0, attemptCount - 1);
        retryAt(when, reason);
    }

    /** Re-queues a failed entry after the user chose to retry. */
    public void requeue(Instant when) {
        this.status = Status.PENDING;
        this.nextAttemptAt = when;
        this.attemptCount = 0;
        this.lastError = null;
        release();
    }

    private void release() { this.lockedBy = null; this.lockedUntil = null; }

    private static String trim(String s) { return s == null ? null : (s.length() > 1000 ? s.substring(0, 1000) : s); }

    public boolean isActive() { return status == Status.PENDING || status == Status.LOCKED; }

    public UUID getId() { return id; }
    public UUID getPostId() { return postId; }
    public UUID getApprovalId() { return approvalId; }
    public Instant getScheduledAt() { return scheduledAt; }
    public Status getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }
    public Instant getLockedUntil() { return lockedUntil; }
}
