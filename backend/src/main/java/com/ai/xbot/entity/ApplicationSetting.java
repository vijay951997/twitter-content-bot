package com.ai.xbot.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "application_settings")
public class ApplicationSetting {

    @Id
    @Column(name = "setting_key", length = 100)
    private String key;

    @Column(name = "setting_value", nullable = false)
    private String value;

    @Column(length = 300)
    private String description;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ApplicationSetting() {}

    public ApplicationSetting(String key, String value, String description) {
        this.key = key;
        this.value = value;
        this.description = description;
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
    public String getDescription() { return description; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String newValue, UUID userId) {
        this.value = newValue;
        this.updatedBy = userId;
    }
}
