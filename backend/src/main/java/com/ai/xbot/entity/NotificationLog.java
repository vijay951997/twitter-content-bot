package com.ai.xbot.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** A dashboard notification. Email/Telegram channels can be added later without schema changes. */
@Entity
@Table(name = "notification_logs")
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false, length = 20)
    private String channel = "DASHBOARD";

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String message;

    @Column(name = "related_entity_type", length = 60)
    private String relatedEntityType;

    @Column(name = "related_entity_id")
    private UUID relatedEntityId;

    @Column(name = "read_at")
    private Instant readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected NotificationLog() {}

    public NotificationLog(String type, String title, String message, String entityType, UUID entityId) {
        this.type = type;
        this.title = title.length() > 200 ? title.substring(0, 200) : title;
        this.message = message;
        this.relatedEntityType = entityType;
        this.relatedEntityId = entityId;
    }

    public void markRead(Instant at) { if (readAt == null) readAt = at; }

    public UUID getId() { return id; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getRelatedEntityType() { return relatedEntityType; }
    public UUID getRelatedEntityId() { return relatedEntityId; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }
}
