package com.ai.xbot.integration.openai;

import static com.ai.xbot.support.StubOpenAiServer.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ai.xbot.config.AppProperties;
import com.ai.xbot.service.ai.AiGenerationRequest;
import com.ai.xbot.service.ai.AiGenerationResult;
import com.ai.xbot.service.ai.AiServiceException;
import com.ai.xbot.service.ai.AiServiceException.Kind;
import com.ai.xbot.support.StubOpenAiServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class OpenAiDraftGeneratorTest {

    private static final String KEY = "sk-test-not-real";
    private final ObjectMapper om = new ObjectMapper();
    private StubOpenAiServer stub;

    @BeforeEach
    void start() throws Exception { stub = new StubOpenAiServer(); }

    @AfterEach
    void stop() { stub.close(); }

    private OpenAiDraftGenerator client(String key, String model, int timeoutSeconds, int retries) {
        AppProperties props = new AppProperties("Asia/Kolkata", null,
                new AppProperties.OpenAi(key, model, stub.baseUrl(), timeoutSeconds, retries, 10L), null, null);
        return new OpenAiDraftGenerator(props, RestClient.builder(), om);
    }

    private static AiGenerationRequest req() { return new AiGenerationRequest("system", "user"); }

    @Test
    @DisplayName("Sends model, strict JSON schema and bearer key; parses drafts and usage")
    void happyPath() throws Exception {
        stub.reply(200, completion(drafts(draft("Hello Chennai", "en", List.of(), "UNVERIFIED"))));
        AiGenerationResult r = client(KEY, "test-model", 5, 0).generate(req());

        assertThat(r.drafts()).hasSize(1);
        assertThat(r.drafts().get(0).content()).isEqualTo("Hello Chennai");
        

        StubOpenAiServer.Received sent = stub.received().get(0);
        assertThat(sent.authorization()).isEqualTo("Bearer " + KEY);
        JsonNode body = om.readTree(sent.body());
        assertThat(body.path("model").asText()).isEqualTo("test-model");
        assertThat(body.path("response_format").path("type").asText()).isEqualTo("json_schema");
        assertThat(body.path("response_format").path("json_schema").path("strict").asBoolean()).isTrue();
        assertThat(body.toString()).doesNotContain(KEY);
    }

    @Test
    @DisplayName("Strict schema: every object lists all properties as required and forbids extras")
    void schemaIsStrictCompatible() {
        JsonNode schema = om.valueToTree(DraftOutputSchema.schema());
        JsonNode item = schema.path("properties").path("drafts").path("items");
        assertThat(schema.path("additionalProperties").asBoolean(true)).isFalse();
        assertThat(item.path("additionalProperties").asBoolean(true)).isFalse();
        assertThat(item.path("required").size()).isEqualTo(item.path("properties").size());
    }

    @Test
    @DisplayName("Missing key or model fails fast without any HTTP call")
    void notConfigured() {
        assertThatThrownBy(() -> client("", "m", 5, 0).generate(req()))
                .isInstanceOf(AiServiceException.class)
                .extracting(e -> ((AiServiceException) e).getKind()).isEqualTo(Kind.NOT_CONFIGURED);
        assertThatThrownBy(() -> client(KEY, "", 5, 0).generate(req()))
                .extracting(e -> ((AiServiceException) e).getKind()).isEqualTo(Kind.NOT_CONFIGURED);
        assertThat(stub.received()).isEmpty();
    }

    @Test
    @DisplayName("401 is not retried")
    void unauthorizedNotRetried() {
        stub.reply(401, "{\"error\":{\"message\":\"bad key\"}}");
        assertThatThrownBy(() -> client(KEY, "m", 5, 3).generate(req()))
                .extracting(e -> ((AiServiceException) e).getKind()).isEqualTo(Kind.AUTH_FAILED);
        assertThat(stub.received()).hasSize(1);
    }

    @Test
    @DisplayName("429 is retried with backoff and can then succeed")
    void rateLimitRetried() {
        stub.reply(429, "{}").reply(200, completion(drafts(draft("ok post", "en", List.of(), "UNVERIFIED"))));
        AiGenerationResult r = client(KEY, "m", 5, 2).generate(req());
        assertThat(r.drafts()).hasSize(1);
        assertThat(stub.received()).hasSize(2);
    }

    @Test
    @DisplayName("Persistent 5xx gives UNAVAILABLE after all retries")
    void serverErrorExhaustsRetries() {
        stub.reply(503, "{}");
        assertThatThrownBy(() -> client(KEY, "m", 5, 2).generate(req()))
                .extracting(e -> ((AiServiceException) e).getKind()).isEqualTo(Kind.UNAVAILABLE);
        assertThat(stub.received()).hasSize(3);
    }

    @Test
    @DisplayName("Negative #7: OpenAI timeout is reported as TIMEOUT")
    void timeout() {
        stub.reply(200, completion(drafts()), 3000);
        assertThatThrownBy(() -> client(KEY, "m", 1, 0).generate(req()))
                .extracting(e -> ((AiServiceException) e).getKind()).isEqualTo(Kind.TIMEOUT);
    }

    @Test
    @DisplayName("400 (e.g. unknown model) surfaces OpenAI's message and is not retried")
    void badRequest() {
        stub.reply(400, "{\"error\":{\"message\":\"The model `nope` does not exist\"}}");
        assertThatThrownBy(() -> client(KEY, "nope", 5, 2).generate(req()))
                .hasMessageContaining("does not exist");
        assertThat(stub.received()).hasSize(1);
    }

    @Test
    @DisplayName("A model refusal is reported, not parsed as drafts")
    void refusal() {
        stub.reply(200, "{\"choices\":[{\"message\":{\"content\":null,\"refusal\":\"I can't help with that\"},"
                + "\"finish_reason\":\"stop\"}]}");
        assertThatThrownBy(() -> client(KEY, "m", 5, 0).generate(req()))
                .extracting(e -> ((AiServiceException) e).getKind()).isEqualTo(Kind.REFUSED);
    }

    @Test
    @DisplayName("Truncated output is rejected")
    void truncated() {
        stub.reply(200, "{\"choices\":[{\"message\":{\"content\":\"{\\\"drafts\\\":[\"},\"finish_reason\":\"length\"}]}");
        assertThatThrownBy(() -> client(KEY, "m", 5, 0).generate(req()))
                .extracting(e -> ((AiServiceException) e).getKind()).isEqualTo(Kind.BAD_RESPONSE);
    }
}
