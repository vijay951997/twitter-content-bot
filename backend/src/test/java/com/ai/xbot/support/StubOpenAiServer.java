package com.ai.xbot.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Minimal local HTTP server standing in for the OpenAI API in tests. Responses are served in order;
 * when the queue is empty the last response repeats. No real OpenAI call is ever made by the test suite.
 */
public class StubOpenAiServer implements AutoCloseable {

    public record Reply(int status, String body, long delayMs) {}
    public record Received(String authorization, String body) {}

    private final HttpServer server;
    private final ConcurrentLinkedQueue<Reply> replies = new ConcurrentLinkedQueue<>();
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private volatile Reply last = new Reply(500, "{}", 0);

    public StubOpenAiServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(new Received(exchange.getRequestHeaders().getFirst("Authorization"), body));
            Reply r = replies.poll();
            if (r == null) r = last; else last = r;
            if (r.delayMs() > 0) {
                try { Thread.sleep(r.delayMs()); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
            byte[] bytes = r.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(r.status(), bytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
            } catch (IOException clientGone) {
                // client timed out and closed the connection
            }
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    public StubOpenAiServer reply(int status, String body) { return reply(status, body, 0); }

    public StubOpenAiServer reply(int status, String body, long delayMs) {
        replies.add(new Reply(status, body, delayMs));
        return this;
    }

    public void reset() {
        replies.clear();
        received.clear();
        last = new Reply(500, "{}", 0);
    }

    public List<Received> received() { return received; }

    /** Wraps draft JSON the way Chat Completions returns it. */
    public static String completion(String draftsJson) {
        String escaped = draftsJson.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return "{\"id\":\"chatcmpl-test\",\"object\":\"chat.completion\",\"choices\":[{\"index\":0,"
                + "\"message\":{\"role\":\"assistant\",\"content\":\"" + escaped + "\",\"refusal\":null},"
                + "\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":120,\"completion_tokens\":80}}";
    }

    public static String draft(String content, String language, List<String> urls, String status) {
        StringBuilder u = new StringBuilder("[");
        for (int i = 0; i < urls.size(); i++) u.append(i > 0 ? "," : "").append('"').append(urls.get(i)).append('"');
        u.append(']');
        return "{\"content\":\"" + content.replace("\"", "\\\"") + "\",\"language\":\"" + language
                + "\",\"category\":\"governance\",\"source_urls\":" + u
                + ",\"verified_claims\":[\"claim A\"],\"unverified_claims\":[],\"verification_status\":\"" + status
                + "\",\"risk_flags\":[],\"quality_estimate\":0.8,\"requires_human_approval\":true}";
    }

    public static String drafts(String... drafts) {
        return "{\"drafts\":[" + String.join(",", drafts) + "]}";
    }

    @Override
    public void close() {
        server.stop(0);
    }

}
