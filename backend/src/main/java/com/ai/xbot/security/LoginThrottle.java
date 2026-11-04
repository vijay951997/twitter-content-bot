package com.ai.xbot.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Slows down password guessing on a public server: after N failed logins within the window, further
 * attempts from that IP address, or for that username, are refused with HTTP 429 until the window passes.
 * In memory (resets on restart), which is enough for a single-instance personal deployment.
 */
@Component
public class LoginThrottle {

    private final int maxFailures;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginThrottle(@Value("${app.login.max-failures:10}") int maxFailures,
                         @Value("${app.login.window-minutes:15}") int windowMinutes, Clock clock) {
        this.maxFailures = maxFailures;
        this.window = Duration.ofMinutes(windowMinutes);
        this.clock = clock;
    }

    public boolean isBlocked(String ip, String username) {
        return count("ip:" + ip) >= maxFailures || (username != null && count("user:" + norm(username)) >= maxFailures);
    }

    public void recordFailure(String ip, String username) {
        Instant now = Instant.now(clock);
        add("ip:" + ip, now);
        if (username != null && !username.isBlank()) add("user:" + norm(username), now);
    }

    public void recordSuccess(String ip, String username) {
        failures.remove("ip:" + ip);
        if (username != null) failures.remove("user:" + norm(username));
    }

    public long retryAfterSeconds() {
        return window.toSeconds();
    }

    private void add(String key, Instant now) {
        Deque<Instant> d = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (d) {
            d.addLast(now);
            while (d.size() > maxFailures * 2) d.removeFirst();
        }
        if (failures.size() > 10_000) failures.clear(); // bound memory under a flood
    }

    private int count(String key) {
        Deque<Instant> d = failures.get(key);
        if (d == null) return 0;
        Instant cutoff = Instant.now(clock).minus(window);
        synchronized (d) {
            while (!d.isEmpty() && d.peekFirst().isBefore(cutoff)) d.removeFirst();
            return d.size();
        }
    }

    private static String norm(String u) {
        return u.strip().toLowerCase(Locale.ROOT);
    }
}
