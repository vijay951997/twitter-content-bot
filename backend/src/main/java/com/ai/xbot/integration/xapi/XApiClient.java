package com.ai.xbot.integration.xapi;

import com.ai.xbot.config.AppProperties;
import com.ai.xbot.integration.xapi.XApiException.Kind;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * X API v2 calls used by the app: OAuth 2.0 token exchange / refresh / revoke (confidential client,
 * HTTP Basic auth), GET /2/users/me and POST /2/tweets. Tokens are never logged.
 */
@Component
public class XApiClient {

    private static final Logger log = LoggerFactory.getLogger(XApiClient.class);

    public record TokenResponse(String accessToken, String refreshToken, long expiresInSeconds, String scope) {}
    public record XUser(String id, String username, String name) {}
    public record CreatedPost(String id, String text) {}

    private final AppProperties.X config;
    private final ObjectMapper om;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public XApiClient(AppProperties properties, ObjectMapper om) {
        this.config = properties.x();
        this.om = om;
    }

    public TokenResponse exchangeCode(String code, String codeVerifier) {
        return token(Map.of("grant_type", "authorization_code", "code", code,
                "redirect_uri", config.redirectUri(), "code_verifier", codeVerifier, "client_id", config.clientId()));
    }

    public TokenResponse refresh(String refreshToken) {
        return token(Map.of("grant_type", "refresh_token", "refresh_token", refreshToken, "client_id", config.clientId()));
    }

    public void revoke(String token, String hint) {
        try {
            send(form("/oauth2/revoke", Map.of("token", token, "token_type_hint", hint, "client_id", config.clientId())));
        } catch (XApiException e) {
            log.warn("X token revoke failed: {}", e.getMessage()); // best effort; local tokens are deleted anyway
        }
    }

    public XUser me(String accessToken) {
        JsonNode r = send(HttpRequest.newBuilder(uri("/users/me")).timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + accessToken).GET().build());
        JsonNode d = r.path("data");
        return new XUser(d.path("id").asText(), d.path("username").asText(null), d.path("name").asText(null));
    }

    public CreatedPost createPost(String accessToken, String text) {
        String body;
        try {
            body = om.writeValueAsString(Map.of("text", text));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        JsonNode r = send(HttpRequest.newBuilder(uri("/tweets")).timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
        String id = r.path("data").path("id").asText(null);
        if (id == null || id.isBlank()) throw new XApiException(Kind.SERVER_ERROR, 200, "X did not return a post id", null);
        return new CreatedPost(id, r.path("data").path("text").asText(text));
    }

    private TokenResponse token(Map<String, String> params) {
        JsonNode r = send(form("/oauth2/token", params));
        String access = r.path("access_token").asText(null);
        if (access == null) throw new XApiException(Kind.BAD_REQUEST, 200, "X did not return an access token", null);
        return new TokenResponse(access, r.path("refresh_token").asText(null), r.path("expires_in").asLong(7200),
                r.path("scope").asText(null));
    }

    private HttpRequest form(String path, Map<String, String> params) {
        String body = new LinkedHashMap<>(params).entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue())).collect(Collectors.joining("&"));
        String basic = Base64.getEncoder().encodeToString(
                (enc(config.clientId()) + ":" + enc(config.clientSecret())).getBytes(StandardCharsets.UTF_8));
        return HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(20))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private JsonNode send(HttpRequest req) {
        HttpResponse<String> r;
        try {
            r = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new XApiException(Kind.NETWORK, 0, "Interrupted", null);
        } catch (Exception e) {
            throw new XApiException(Kind.NETWORK, 0, "Could not reach X: " + e.getClass().getSimpleName(), null);
        }
        int s = r.statusCode();
        if (s >= 200 && s < 300) {
            try {
                return r.body() == null || r.body().isBlank() ? om.createObjectNode() : om.readTree(r.body());
            } catch (Exception e) {
                throw new XApiException(Kind.SERVER_ERROR, s, "Unreadable response from X", null);
            }
        }
        String detail = detail(r.body());
        log.warn("X API {} {} -> HTTP {}: {}", req.method(), req.uri().getPath(), s, detail);
        Long retryAfter = retryAfter(r);
        throw switch (s) {
            case 401 -> new XApiException(Kind.UNAUTHORIZED, s, "X rejected the access token (401)", null);
            case 402 -> new XApiException(Kind.PAYMENT_REQUIRED, s, "X API credits exhausted or billing required (402): " + detail, null);
            case 403 -> detail.toLowerCase().contains("duplicate")
                    ? new XApiException(Kind.DUPLICATE, s, "X refused a duplicate post: " + detail, null)
                    : new XApiException(Kind.FORBIDDEN, s, "X refused the request (403): " + detail, null);
            case 429 -> new XApiException(Kind.RATE_LIMITED, s, "X rate limit reached (429)", retryAfter);
            default -> s >= 500 ? new XApiException(Kind.SERVER_ERROR, s, "X server error (" + s + ")", retryAfter)
                    : new XApiException(Kind.BAD_REQUEST, s, "X rejected the request (" + s + "): " + detail, null);
        };
    }

    /** Seconds until retry from Retry-After or x-rate-limit-reset (epoch seconds). */
    private static Long retryAfter(HttpResponse<String> r) {
        try {
            var ra = r.headers().firstValue("retry-after");
            if (ra.isPresent()) return Long.parseLong(ra.get().trim());
            var reset = r.headers().firstValue("x-rate-limit-reset");
            if (reset.isPresent()) return Math.max(1, Long.parseLong(reset.get().trim()) - System.currentTimeMillis() / 1000);
        } catch (NumberFormatException ignored) {
            // fall back to default backoff
        }
        return null;
    }

    private String detail(String body) {
        try {
            JsonNode n = om.readTree(body);
            String d = n.path("detail").asText("");
            if (d.isBlank()) d = n.path("error_description").asText("");
            if (d.isBlank()) d = n.path("title").asText("");
            if (d.isBlank() && n.path("errors").isArray() && !n.path("errors").isEmpty()) d = n.path("errors").get(0).path("message").asText("");
            return d.isBlank() ? "no details" : (d.length() > 300 ? d.substring(0, 300) : d);
        } catch (Exception e) {
            return "no details";
        }
    }

    private URI uri(String path) {
        return URI.create(config.effectiveApiBaseUrl() + path);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
