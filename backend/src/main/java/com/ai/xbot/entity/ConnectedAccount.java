package com.ai.xbot.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** The X account the app posts as. Tokens are stored only in encrypted form (AES-256-GCM). */
@Entity
@Table(name = "connected_accounts")
public class ConnectedAccount {

    public enum Status { ACTIVE, EXPIRED, REVOKED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String provider = "X";

    @Column(name = "provider_user_id", nullable = false, length = 64)
    private String providerUserId;

    @Column(name = "provider_username", length = 64)
    private String providerUsername;

    @Column(name = "access_token_encrypted", columnDefinition = "text")
    private String accessTokenEncrypted;

    @Column(name = "refresh_token_encrypted", columnDefinition = "text")
    private String refreshTokenEncrypted;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(length = 500)
    private String scopes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Version
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConnectedAccount() {}

    public ConnectedAccount(UUID userId, String providerUserId, String providerUsername) {
        this.userId = userId;
        this.providerUserId = providerUserId;
        this.providerUsername = providerUsername;
    }

    public void storeTokens(String accessEnc, String refreshEnc, Instant expiresAt, String scopes) {
        this.accessTokenEncrypted = accessEnc;
        if (refreshEnc != null) this.refreshTokenEncrypted = refreshEnc;
        this.tokenExpiresAt = expiresAt;
        if (scopes != null) this.scopes = scopes;
        this.status = Status.ACTIVE;
    }

    public void reconnect(UUID userId, String username) {
        this.userId = userId;
        this.providerUsername = username;
    }

    public void markExpired() { this.status = Status.EXPIRED; }

    public void revoke() {
        this.status = Status.REVOKED;
        this.accessTokenEncrypted = null;
        this.refreshTokenEncrypted = null;
        this.tokenExpiresAt = null;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getProviderUserId() { return providerUserId; }
    public String getProviderUsername() { return providerUsername; }
    public String getAccessTokenEncrypted() { return accessTokenEncrypted; }
    public String getRefreshTokenEncrypted() { return refreshTokenEncrypted; }
    public Instant getTokenExpiresAt() { return tokenExpiresAt; }
    public String getScopes() { return scopes; }
    public Status getStatus() { return status; }
    public Instant getUpdatedAt() { return updatedAt; }
}
