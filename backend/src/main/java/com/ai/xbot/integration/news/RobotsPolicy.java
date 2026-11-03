package com.ai.xbot.integration.news;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Honours robots.txt for every fetch. Rules for our user agent take precedence over "*";
 * the longest matching Allow/Disallow path wins (as in RFC 9309). Results are cached per host for 24h.
 * A missing robots.txt (4xx) means allowed; a server error (5xx) or network failure means "disallowed"
 * for that run, as RFC 9309 recommends.
 */
@Component
public class RobotsPolicy {

    private static final Logger log = LoggerFactory.getLogger(RobotsPolicy.class);
    static final String AGENT_TOKEN = "xcontentdeskbot";
    private static final Duration TTL = Duration.ofHours(24);

    record Rule(boolean allow, String path) {}
    record Entry(List<Rule> rules, Instant fetchedAt, boolean unreachable) {}

    private final HttpFetcher http;
    private final Clock clock;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public RobotsPolicy(HttpFetcher http, Clock clock) {
        this.http = http;
        this.clock = clock;
    }

    public enum Verdict { ALLOWED, DISALLOWED, UNREACHABLE }

    public boolean isAllowed(String url) {
        return check(url) == Verdict.ALLOWED;
    }

    /** UNREACHABLE means robots.txt could not be read (5xx or network error), so we must not fetch now. */
    public Verdict check(String url) {
        URI uri = URI.create(url);
        String origin = uri.getScheme() + "://" + uri.getRawAuthority();
        Entry e = cache.get(origin);
        if (e == null || e.fetchedAt().plus(e.unreachable() ? Duration.ofMinutes(30) : TTL).isBefore(Instant.now(clock))) {
            e = load(origin);
            cache.put(origin, e);
        }
        if (e.unreachable()) return Verdict.UNREACHABLE;
        String path = (uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        return allowed(e.rules(), path) ? Verdict.ALLOWED : Verdict.DISALLOWED;
    }

    private Entry load(String origin) {
        try {
            HttpFetcher.Response r = http.get(origin + "/robots.txt", Map.of());
            if (r.status() >= 400 && r.status() < 500) return new Entry(List.of(), Instant.now(clock), false);
            if (r.status() >= 500) return new Entry(List.of(), Instant.now(clock), true);
            return new Entry(parse(new String(r.body(), StandardCharsets.UTF_8)), Instant.now(clock), false);
        } catch (Exception ex) {
            log.warn("robots.txt unreachable for {}: {}", origin, ex.getMessage());
            return new Entry(List.of(), Instant.now(clock), true);
        }
    }

    /** Returns the rules that apply to us: our own group if present, otherwise the "*" group. */
    static List<Rule> parse(String robots) {
        Map<String, List<Rule>> groups = new HashMap<>();
        List<String> currentAgents = new ArrayList<>();
        boolean lastWasAgent = false;
        for (String raw : robots.split("\\R")) {
            String line = raw.replaceAll("#.*", "").trim();
            if (line.isEmpty()) continue;
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            switch (key) {
                case "user-agent" -> {
                    if (!lastWasAgent) currentAgents = new ArrayList<>();
                    currentAgents.add(value.toLowerCase(Locale.ROOT));
                    groups.putIfAbsent(value.toLowerCase(Locale.ROOT), new ArrayList<>());
                    lastWasAgent = true;
                }
                case "allow", "disallow" -> {
                    lastWasAgent = false;
                    if (value.isEmpty()) continue; // "Disallow:" with no path allows everything
                    for (String a : currentAgents) groups.get(a).add(new Rule(key.equals("allow"), value));
                }
                default -> lastWasAgent = false;
            }
        }
        for (String agent : groups.keySet()) {
            if (!agent.equals("*") && AGENT_TOKEN.contains(agent)) return groups.get(agent);
        }
        return groups.getOrDefault("*", List.of());
    }

    static boolean allowed(List<Rule> rules, String path) {
        Rule best = null;
        for (Rule r : rules) {
            if (matches(r.path(), path) && (best == null || r.path().length() > best.path().length()
                    || (r.path().length() == best.path().length() && r.allow()))) {
                best = r;
            }
        }
        return best == null || best.allow();
    }

    /** Prefix match supporting '*' wildcards and a trailing '$' anchor. */
    static boolean matches(String pattern, String path) {
        boolean anchored = pattern.endsWith("$");
        String p = anchored ? pattern.substring(0, pattern.length() - 1) : pattern;
        StringBuilder regex = new StringBuilder();
        String[] parts = p.split("\\*", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) regex.append(".*");
            regex.append(java.util.regex.Pattern.quote(parts[i]));
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex.toString()).matcher(path);
        return anchored ? m.matches() : m.lookingAt();
    }
}
