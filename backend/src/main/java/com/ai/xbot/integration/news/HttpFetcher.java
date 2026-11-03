package com.ai.xbot.integration.news;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Small HTTP GET helper with a fixed User-Agent, timeouts and a response size cap. */
@Component
public class HttpFetcher {

    public static final String USER_AGENT = "XContentDeskBot/0.3 (personal news reader; respects robots.txt)";
    static final int MAX_BYTES = 3 * 1024 * 1024;

    public record Response(int status, byte[] body, Map<String, java.util.List<String>> headers, boolean truncated) {
        public String header(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey() != null && e.getKey().equalsIgnoreCase(name))
                    .flatMap(e -> e.getValue().stream()).findFirst().orElse(null);
        }
    }

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public Response get(String url, Map<String, String> extraHeaders) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, text/plain;q=0.8, */*;q=0.5")
                .GET();
        extraHeaders.forEach((k, v) -> { if (v != null) b.header(k, v); });
        HttpResponse<InputStream> r = client.send(b.build(), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = r.body()) {
            byte[] data = in.readNBytes(MAX_BYTES + 1);
            boolean truncated = data.length > MAX_BYTES;
            return new Response(r.statusCode(), data, r.headers().map(), truncated);
        }
    }
}
