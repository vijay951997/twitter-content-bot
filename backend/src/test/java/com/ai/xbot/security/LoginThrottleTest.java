package com.ai.xbot.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LoginThrottleTest {

    static class MutableClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-04T00:00:00Z"));
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }

    @Test
    void blocksAfterLimitPerIpAndPerUsernameThenExpires() {
        MutableClock clock = new MutableClock();
        LoginThrottle t = new LoginThrottle(3, 15, clock);
        for (int i = 0; i < 3; i++) t.recordFailure("1.2.3.4", "admin");
        assertThat(t.isBlocked("1.2.3.4", "someone")).isTrue();   // by IP
        assertThat(t.isBlocked("5.6.7.8", "ADMIN")).isTrue();     // by username (case-insensitive)
        assertThat(t.isBlocked("5.6.7.8", "other")).isFalse();
        clock.now.set(clock.now.get().plus(Duration.ofMinutes(16)));
        assertThat(t.isBlocked("1.2.3.4", "admin")).isFalse();
    }

    @Test
    void successClearsCounters() {
        LoginThrottle t = new LoginThrottle(3, 15, new MutableClock());
        t.recordFailure("1.2.3.4", "admin");
        t.recordFailure("1.2.3.4", "admin");
        t.recordSuccess("1.2.3.4", "admin");
        t.recordFailure("1.2.3.4", "admin");
        assertThat(t.isBlocked("1.2.3.4", "admin")).isFalse();
    }
}
