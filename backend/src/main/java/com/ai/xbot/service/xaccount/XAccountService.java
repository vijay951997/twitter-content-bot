package com.ai.xbot.service.xaccount;

import com.ai.xbot.audit.AuditService;
import com.ai.xbot.config.AppProperties;
import com.ai.xbot.dto.XAccountStatusDto;
import com.ai.xbot.entity.ConnectedAccount;
import com.ai.xbot.entity.enums.ActorType;
import com.ai.xbot.exception.BadRequestException;
import com.ai.xbot.integration.xapi.XApiClient;
import com.ai.xbot.integration.xapi.XApiException;
import com.ai.xbot.repository.ConnectedAccountRepository;
import com.ai.xbot.security.TokenCipher;
import com.ai.xbot.service.notify.NotificationService;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Connects the user's X account with OAuth 2.0 Authorization Code + PKCE (S256). The user signs in on
 * x.com; this app never sees the X password. Access and refresh tokens are stored AES-256-GCM encrypted.
 */
@Service
public class XAccountService {

    private static final Logger log = LoggerFactory.getLogger(XAccountService.class);
    /** Minimum scopes: post, read own profile/posts (for metrics), and offline.access for refresh tokens. */
    public static final String SCOPES = "tweet.read tweet.write users.read offline.access";
    static final String SESSION_KEY = "x_oauth_pending";
    static final Duration PENDING_TTL = Duration.ofMinutes(10);
    static final Duration REFRESH_MARGIN = Duration.ofMinutes(5);

    record Pending(String state, String verifier, Instant createdAt, UUID userId) implements Serializable {}

    private final AppProperties.X config;
    private final XApiClient x;
    private final TokenCipher cipher;
    private final ConnectedAccountRepository accounts;
    private final AuditService audit;
    private final NotificationService notifications;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public XAccountService(AppProperties properties, XApiClient x, TokenCipher cipher, ConnectedAccountRepository accounts,
                           AuditService audit, NotificationService notifications, TransactionTemplate tx, Clock clock) {
        this.config = properties.x();
        this.x = x;
        this.cipher = cipher;
        this.accounts = accounts;
        this.audit = audit;
        this.notifications = notifications;
        this.tx = tx;
        this.clock = clock;
    }

    public XAccountStatusDto status() {
        boolean app = config != null && config.clientId() != null && !config.clientId().isBlank()
                && config.clientSecret() != null && !config.clientSecret().isBlank()
                && config.redirectUri() != null && !config.redirectUri().isBlank();
        List<String> missing = new ArrayList<>();
        if (config == null || blank(config.clientId())) missing.add("X_CLIENT_ID");
        if (config == null || blank(config.clientSecret())) missing.add("X_CLIENT_SECRET");
        if (config == null || blank(config.redirectUri())) missing.add("X_REDIRECT_URI");
        if (!cipher.available()) missing.add("APP_TOKEN_ENCRYPTION_KEY");
        Optional<ConnectedAccount> acc = accounts.current();
        return new XAccountStatusDto(app, cipher.available(),
                acc.map(a -> a.getStatus() == ConnectedAccount.Status.ACTIVE).orElse(false),
                acc.map(a -> a.getStatus().name()).orElse("NOT_CONNECTED"),
                acc.map(ConnectedAccount::getProviderUsername).orElse(null),
                acc.map(ConnectedAccount::getUpdatedAt).orElse(null),
                missing.isEmpty() ? null : String.join(", ", missing));
    }

    /** Builds the X authorize URL and remembers state + PKCE verifier in the user's session. */
    public String startConnect(HttpSession session, UUID userId) {
        if (config == null || !config.configured()) {
            throw new BadRequestException("X isn't set up yet. Missing in .env: " + status().missing());
        }
        String state = randomUrlSafe(32);
        String verifier = randomUrlSafe(64);
        session.setAttribute(SESSION_KEY, new Pending(state, verifier, Instant.now(clock), userId));
        return config.effectiveAuthorizeUrl()
                + "?response_type=code"
                + "&client_id=" + enc(config.clientId())
                + "&redirect_uri=" + enc(config.redirectUri())
                + "&scope=" + enc(SCOPES)
                + "&state=" + enc(state)
                + "&code_challenge=" + enc(challenge(verifier))
                + "&code_challenge_method=S256";
    }

    /** Handles X's redirect back. Returns null on success, or a short error code for the dashboard. */
    public String completeConnect(HttpSession session, UUID userId, String state, String code, String error) {
        Object raw = session.getAttribute(SESSION_KEY);
        session.removeAttribute(SESSION_KEY); // single use
        if (error != null) return "denied";
        if (!(raw instanceof Pending p) || state == null || !MessageDigest.isEqual(
                p.state().getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8))) {
            log.warn("X OAuth callback with missing or mismatched state");
            return "state";
        }
        if (!p.userId().equals(userId)) return "state";
        if (p.createdAt().plus(PENDING_TTL).isBefore(Instant.now(clock))) return "expired";
        if (code == null || code.isBlank()) return "code";

        try {
            XApiClient.TokenResponse t = x.exchangeCode(code, p.verifier());
            XApiClient.XUser me = x.me(t.accessToken());
            Instant expires = Instant.now(clock).plusSeconds(t.expiresInSeconds());
            tx.executeWithoutResult(s -> {
                // Only one active account: posts go out as the most recently connected one.
                accounts.findCurrent().stream().filter(a -> !a.getProviderUserId().equals(me.id())).forEach(ConnectedAccount::revoke);
                ConnectedAccount acc = accounts.findByProviderAndProviderUserId("X", me.id())
                        .orElseGet(() -> new ConnectedAccount(userId, me.id(), me.username()));
                acc.reconnect(userId, me.username());
                acc.storeTokens(cipher.encrypt(t.accessToken()), cipher.encrypt(t.refreshToken()), expires, t.scope());
                accounts.save(acc);
                audit.record(userId, ActorType.USER, "X_ACCOUNT_CONNECTED", "connected_accounts", acc.getId(), null,
                        Map.of("username", String.valueOf(me.username()), "scopes", String.valueOf(t.scope()),
                                "refreshToken", t.refreshToken() != null), null);
            });
            log.info("X account @{} connected", me.username());
            return null;
        } catch (XApiException e) {
            log.warn("X OAuth exchange failed: {}", e.getMessage());
            return "exchange";
        }
    }

    /** Revokes tokens at X (best effort) and deletes them locally. */
    public void disconnect(UUID userId) {
        ConnectedAccount acc = accounts.current().orElseThrow(() -> new BadRequestException("No X account is connected"));
        try {
            if (acc.getRefreshTokenEncrypted() != null) x.revoke(cipher.decrypt(acc.getRefreshTokenEncrypted()), "refresh_token");
            if (acc.getAccessTokenEncrypted() != null) x.revoke(cipher.decrypt(acc.getAccessTokenEncrypted()), "access_token");
        } catch (IllegalStateException e) {
            log.warn("Could not decrypt tokens for revocation: {}", e.getMessage());
        }
        tx.executeWithoutResult(s -> {
            ConnectedAccount fresh = accounts.findById(acc.getId()).orElseThrow();
            fresh.revoke();
            audit.record(userId, ActorType.USER, "X_ACCOUNT_DISCONNECTED", "connected_accounts", fresh.getId(), null, null, null);
        });
    }

    /** A valid access token for publishing, refreshing it first when it expires within 5 minutes. */
    public synchronized String accessToken(ConnectedAccount acc, boolean forceRefresh) {
        ConnectedAccount fresh = accounts.findById(acc.getId()).orElseThrow();
        if (fresh.getStatus() != ConnectedAccount.Status.ACTIVE || fresh.getAccessTokenEncrypted() == null) {
            throw new XApiException(XApiException.Kind.UNAUTHORIZED, 401, "X account is not connected", null);
        }
        boolean expiring = fresh.getTokenExpiresAt() != null
                && fresh.getTokenExpiresAt().isBefore(Instant.now(clock).plus(REFRESH_MARGIN));
        if (!forceRefresh && !expiring) return cipher.decrypt(fresh.getAccessTokenEncrypted());

        if (fresh.getRefreshTokenEncrypted() == null) {
            expire(fresh, "X session expired and no refresh token is available. Reconnect X.");
            throw new XApiException(XApiException.Kind.UNAUTHORIZED, 401, "X session expired; reconnect X", null);
        }
        try {
            XApiClient.TokenResponse t = x.refresh(cipher.decrypt(fresh.getRefreshTokenEncrypted()));
            tx.executeWithoutResult(s -> {
                ConnectedAccount a = accounts.findById(fresh.getId()).orElseThrow();
                // X rotates refresh tokens: keep the new one if returned.
                a.storeTokens(cipher.encrypt(t.accessToken()), cipher.encrypt(t.refreshToken()),
                        Instant.now(clock).plusSeconds(t.expiresInSeconds()), t.scope());
                audit.record(null, ActorType.SYSTEM, "X_TOKEN_REFRESHED", "connected_accounts", a.getId(), null, null, null);
            });
            return t.accessToken();
        } catch (XApiException e) {
            if (e.getKind() == XApiException.Kind.BAD_REQUEST || e.getKind() == XApiException.Kind.UNAUTHORIZED) {
                expire(fresh, "X refused to renew the session. Reconnect X in Settings.");
            }
            throw e;
        }
    }

    private void expire(ConnectedAccount acc, String message) {
        tx.executeWithoutResult(s -> accounts.findById(acc.getId()).ifPresent(ConnectedAccount::markExpired));
        notifications.notify("OAUTH_EXPIRING", "X connection expired", message, "connected_accounts", acc.getId());
    }

    static String challenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String randomUrlSafe(int bytes) {
        byte[] b = new byte[bytes];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
}
