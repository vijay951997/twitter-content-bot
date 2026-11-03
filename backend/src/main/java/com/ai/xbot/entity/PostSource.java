package com.ai.xbot.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** A source a draft relies on. In Phase 2 these are user-provided URLs; Phase 3 links articles and claims. */
@Entity
@Table(name = "post_sources")
public class PostSource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "article_id")
    private UUID articleId;

    @Column(name = "claim_id")
    private UUID claimId;

    @Column(name = "source_url", nullable = false, length = 1000)
    private String sourceUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PostSource() {}

    public PostSource(UUID postId, String sourceUrl) {
        this.postId = postId;
        this.sourceUrl = sourceUrl;
    }

    public UUID getId() { return id; }
    public UUID getPostId() { return postId; }
    public String getSourceUrl() { return sourceUrl; }
}
