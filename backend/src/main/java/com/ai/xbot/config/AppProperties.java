package com.ai.xbot.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed application configuration bound from application.yml / environment.
 * Secrets (OpenAI key, X client secret) are read here but never logged or exposed by any endpoint.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String timezone,
        Admin admin,
        OpenAi openai,
        X x,
        Cors cors) {

    public record Admin(String username, String password) {}

    /**
     * @param baseUrl         OpenAI API base URL (overridable for tests or a compatible gateway)
     * @param timeoutSeconds  read timeout per request
     * @param maxRetries      retries for 429 / 5xx / timeouts (never for 4xx client errors)
     * @param retryBackoffMs  initial backoff, doubled per attempt
     */
    public record OpenAi(String apiKey, String model, String baseUrl, Integer timeoutSeconds,
                         Integer maxRetries, Long retryBackoffMs) {
        public boolean configured() {
            return apiKey != null && !apiKey.isBlank() && model != null && !model.isBlank();
        }
        public String effectiveBaseUrl() {
            return baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl;
        }
        public int effectiveTimeoutSeconds() { return timeoutSeconds == null ? 60 : timeoutSeconds; }
        public int effectiveMaxRetries() { return maxRetries == null ? 2 : maxRetries; }
        public long effectiveRetryBackoffMs() { return retryBackoffMs == null ? 1000L : retryBackoffMs; }
    }

    /**
     * @param tokenEncryptionKey base64 of 32 random bytes (AES-256) used to encrypt X tokens at rest
     * @param apiBaseUrl         X API base, e.g. https://api.x.com/2 (overridable for tests)
     * @param authorizeUrl       X OAuth 2.0 authorize page
     */
    public record X(String clientId, String clientSecret, String redirectUri, String tokenEncryptionKey,
                    String apiBaseUrl, String authorizeUrl) {
        public boolean configured() {
            return notBlank(clientId) && notBlank(clientSecret) && notBlank(redirectUri) && notBlank(tokenEncryptionKey);
        }
        public String effectiveApiBaseUrl() { return notBlank(apiBaseUrl) ? apiBaseUrl : "https://api.x.com/2"; }
        public String effectiveAuthorizeUrl() { return notBlank(authorizeUrl) ? authorizeUrl : "https://x.com/i/oauth2/authorize"; }
        private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
    }

    public record Cors(String allowedOrigins) {}
}
