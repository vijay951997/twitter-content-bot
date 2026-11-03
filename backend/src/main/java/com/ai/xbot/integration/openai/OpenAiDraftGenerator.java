package com.ai.xbot.integration.openai;

import com.ai.xbot.config.AppProperties;
import com.ai.xbot.service.ai.AiDraftGenerator;
import com.ai.xbot.service.ai.AiJsonClient;
import com.ai.xbot.service.ai.AiGenerationRequest;
import com.ai.xbot.service.ai.AiGenerationResult;
import com.ai.xbot.service.ai.AiGenerationResult.AiDraft;
import com.ai.xbot.service.ai.AiServiceException;
import com.ai.xbot.service.ai.AiServiceException.Kind;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * OpenAI Chat Completions client using Structured Outputs ({@code response_format: json_schema, strict: true}).
 *
 * Retries: 429, 5xx and timeouts are retried with exponential backoff (configurable); 400/401/403/404
 * are not retried. The API key is sent only in the Authorization header and is never logged.
 */
@Component
public class OpenAiDraftGenerator implements AiDraftGenerator, AiJsonClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiDraftGenerator.class);
    static final String SCHEMA_NAME = "x_post_drafts";

    private final AppProperties.OpenAi config;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiDraftGenerator(AppProperties properties, RestClient.Builder builder, ObjectMapper objectMapper) {
        this.config = properties.openai();
        this.objectMapper = objectMapper;
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(config == null ? 60 : config.effectiveTimeoutSeconds()));
        this.restClient = builder
                // Buffer the (small) JSON body so a Content-Length header is sent instead of chunked encoding;
                // some proxies and OpenAI-compatible gateways reject chunked requests.
                .requestFactory(new BufferingClientHttpRequestFactory(factory))
                .baseUrl(config == null ? "https://api.openai.com/v1" : config.effectiveBaseUrl())
                .build();
    }

    @Override
    public String modelName() {
        return config == null ? null : config.model();
    }

    @Override
    public AiGenerationResult generate(AiGenerationRequest request) {
        JsonNode out = complete(request.systemPrompt(), request.userPrompt(), SCHEMA_NAME, DraftOutputSchema.schema());
        List<AiDraft> drafts = new ArrayList<>();
        for (JsonNode d : out.path("drafts")) {
            drafts.add(new AiDraft(
                    d.path("content").asText(""),
                    d.path("language").asText(null),
                    d.path("category").asText(null),
                    strings(d.path("source_urls")),
                    strings(d.path("verified_claims")),
                    strings(d.path("unverified_claims")),
                    d.path("verification_status").asText(null),
                    strings(d.path("risk_flags")),
                    d.path("quality_estimate").isNumber() ? d.path("quality_estimate").asDouble() : null));
        }
        return new AiGenerationResult(drafts, out.toString(), null, null);
    }

    @Override
    public JsonNode complete(String systemPrompt, String userPrompt, String schemaName, Map<String, Object> schema) {
        if (config == null || !config.configured()) {
            throw new AiServiceException(Kind.NOT_CONFIGURED,
                    "OpenAI is not configured. Set OPENAI_API_KEY and OPENAI_MODEL in .env and restart the backend.");
        }
        Map<String, Object> body = requestBody(systemPrompt, userPrompt, schemaName, schema);

        int attempts = config.effectiveMaxRetries() + 1;
        long backoff = config.effectiveRetryBackoffMs();
        AiServiceException last = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                String response = restClient.post()
                        .uri("/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class);
                return parse(response, schemaName);
            } catch (HttpStatusCodeException e) {
                int status = e.getStatusCode().value();
                if (status == 401 || status == 403) {
                    throw new AiServiceException(Kind.AUTH_FAILED,
                            "OpenAI rejected the API key (HTTP " + status + "). Check OPENAI_API_KEY.");
                }
                if (status == 429) {
                    last = new AiServiceException(Kind.RATE_LIMITED,
                            "OpenAI rate limit or quota reached (HTTP 429). Check your OpenAI usage limits and billing.");
                } else if (status >= 500) {
                    last = new AiServiceException(Kind.UNAVAILABLE, "OpenAI is unavailable (HTTP " + status + ").");
                } else {
                    throw new AiServiceException(Kind.BAD_RESPONSE,
                            "OpenAI refused the request (HTTP " + status + "): " + errorMessage(e.getResponseBodyAsString()));
                }
            } catch (ResourceAccessException e) {
                boolean timeout = isTimeout(e);
                last = new AiServiceException(timeout ? Kind.TIMEOUT : Kind.UNAVAILABLE,
                        timeout ? "OpenAI did not respond in time." : "Could not reach OpenAI.", e);
            }
            log.warn("OpenAI call failed (attempt {}/{}): {}", attempt, attempts, last.getMessage());
            if (attempt < attempts) {
                sleep(backoff);
                backoff *= 2;
            }
        }
        throw last;
    }

    Map<String, Object> requestBody(String systemPrompt, String userPrompt, String schemaName, Map<String, Object> schema) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
        body.put("response_format", Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", schemaName,
                        "strict", true,
                        "schema", schema)));
        return body;
    }

    JsonNode parse(String response, String schemaName) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode choice = root.path("choices").path(0);
            JsonNode message = choice.path("message");

            if (message.hasNonNull("refusal") && !message.get("refusal").asText().isBlank()) {
                throw new AiServiceException(Kind.REFUSED, "The model declined: " + message.get("refusal").asText());
            }
            if ("length".equals(choice.path("finish_reason").asText())) {
                throw new AiServiceException(Kind.BAD_RESPONSE, "The model's answer was cut off (token limit).");
            }
            String content = message.path("content").asText(null);
            if (content == null || content.isBlank()) {
                throw new AiServiceException(Kind.BAD_RESPONSE, "OpenAI returned an empty answer.");
            }
            JsonNode usage = root.path("usage");
            log.info("OpenAI call ok schema={} model={} promptTokens={} completionTokens={}", schemaName, config.model(),
                    usage.path("prompt_tokens").asText("?"), usage.path("completion_tokens").asText("?"));
            return objectMapper.readTree(content);
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new AiServiceException(Kind.BAD_RESPONSE, "Could not read OpenAI's answer.", e);
        }
    }

    static List<String> strings(JsonNode array) {
        List<String> list = new ArrayList<>();
        if (array != null && array.isArray()) array.forEach(n -> { if (!n.asText("").isBlank()) list.add(n.asText().trim()); });
        return list;
    }


    private String errorMessage(String body) {
        try {
            String msg = objectMapper.readTree(body).path("error").path("message").asText("");
            return msg.isBlank() ? "no details" : msg;
        } catch (Exception e) {
            return "no details";
        }
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException || t instanceof HttpTimeoutException) return true;
        }
        return false;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new AiServiceException(Kind.UNAVAILABLE, "Interrupted while retrying OpenAI");
        }
    }
}
