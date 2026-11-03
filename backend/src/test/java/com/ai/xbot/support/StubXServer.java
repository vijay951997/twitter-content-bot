package com.ai.xbot.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Local stand-in for the X API v2 (OAuth token/revoke, users/me, tweets). Never calls the real X. */
public class StubXServer implements AutoCloseable {

    public record Call(String path, String authorization, String body) {}
    public record Reply(int status, String body, Map<String, String> headers) {
        public static Reply ok(String body) { return new Reply(200, body, Map.of()); }
    }

    private final HttpServer server;
    public final List<Call> calls = new CopyOnWriteArrayList<>();
    public final ConcurrentLinkedQueue<Reply> tweetReplies = new ConcurrentLinkedQueue<>();
    public volatile Reply refreshReply = null; // null = succeed
    private final AtomicInteger tokenCounter = new AtomicInteger();
    private final AtomicInteger postCounter = new AtomicInteger(1000);
    public volatile long tweetDelayMs = 0;

    public StubXServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/2/oauth2/token", ex -> {
            String body = read(ex);
            record(ex, body);
            if (body.contains("grant_type=refresh_token") && refreshReply != null) { send(ex, refreshReply); return; }
            int n = tokenCounter.incrementAndGet();
            send(ex, Reply.ok("{\"token_type\":\"bearer\",\"expires_in\":7200,\"access_token\":\"acc-" + n
                    + "\",\"refresh_token\":\"ref-" + n + "\",\"scope\":\"tweet.read tweet.write users.read offline.access\"}"));
        });
        server.createContext("/2/oauth2/revoke", ex -> { record(ex, read(ex)); send(ex, Reply.ok("{\"revoked\":true}")); });
        server.createContext("/2/users/me", ex -> {
            record(ex, "");
            send(ex, Reply.ok("{\"data\":{\"id\":\"777\",\"username\":\"vijay_test\",\"name\":\"Vijay\"}}"));
        });
        server.createContext("/2/tweets", ex -> {
            String body = read(ex);
            record(ex, body);
            if (tweetDelayMs > 0) { try { Thread.sleep(tweetDelayMs); } catch (InterruptedException ignored) { } }
            Reply r = tweetReplies.poll();
            if (r == null) {
                String text = body.replaceAll("(?s).*\"text\":\"(.*)\".*", "$1");
                r = new Reply(201, "{\"data\":{\"id\":\"" + postCounter.incrementAndGet() + "\",\"text\":\"" + text + "\"}}", Map.of());
            }
            send(ex, r);
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
    }

    public String apiBase() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/2"; }

    public long count(String path) { return calls.stream().filter(c -> c.path().equals(path)).count(); }

    public Call last(String path) {
        Call last = null;
        for (Call c : calls) if (c.path().equals(path)) last = c;
        return last;
    }

    private void record(HttpExchange ex, String body) {
        calls.add(new Call(ex.getRequestURI().getPath(), ex.getRequestHeaders().getFirst("Authorization"), body));
    }

    private static String read(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void send(HttpExchange ex, Reply r) throws IOException {
        byte[] b = r.body().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        r.headers().forEach((k, v) -> ex.getResponseHeaders().add(k, v));
        ex.sendResponseHeaders(r.status(), b.length);
        try (var os = ex.getResponseBody()) { os.write(b); }
    }

    @Override
    public void close() { server.stop(0); }
}
