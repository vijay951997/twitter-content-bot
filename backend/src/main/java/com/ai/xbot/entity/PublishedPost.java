package com.ai.xbot.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** Record of a post that is live on X. UNIQUE(post_id) is the last guard against double publishing. */
@Entity
@Table(name = "published_posts")
public class PublishedPost {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "post_id", nullable = false, unique = true)
    private UUID postId;

    @Column(name = "approval_id", nullable = false)
    private UUID approvalId;

    @Column(name = "connected_account_id", nullable = false)
    private UUID connectedAccountId;

    @Column(name = "x_post_id", nullable = false, unique = true, length = 64)
    private String xPostId;

    @Column(name = "published_content", nullable = false, columnDefinition = "text")
    private String publishedContent;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PublishedPost() {}

    public PublishedPost(UUID postId, UUID approvalId, UUID connectedAccountId, String xPostId, String content, Instant at) {
        this.postId = postId;
        this.approvalId = approvalId;
        this.connectedAccountId = connectedAccountId;
        this.xPostId = xPostId;
        this.publishedContent = content;
        this.publishedAt = at;
    }

    public UUID getId() { return id; }
    public UUID getPostId() { return postId; }
    public String getXPostId() { return xPostId; }
    public String getPublishedContent() { return publishedContent; }
    public Instant getPublishedAt() { return publishedAt; }
    public UUID getConnectedAccountId() { return connectedAccountId; }
}
