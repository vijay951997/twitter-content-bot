package com.ai.xbot.scheduler;

import com.ai.xbot.service.publishing.PublisherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the publisher every 30 seconds. Only approved, scheduled queue entries are ever touched.
 * A PostgreSQL advisory lock keeps cycles from overlapping across instances (row locks add a second guard).
 */
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class PublishingScheduler {

    private static final Logger log = LoggerFactory.getLogger(PublishingScheduler.class);
    static final long LOCK_KEY = 0x58424f54_50554253L; // "XBOTPUBS"

    private final PublisherService publisher;
    private final JdbcTemplate jdbc;

    public PublishingScheduler(PublisherService publisher, JdbcTemplate jdbc) {
        this.publisher = publisher;
        this.jdbc = jdbc;
    }

    @Scheduled(initialDelayString = "PT20S", fixedDelayString = "PT30S")
    public void run() {
        jdbc.execute((java.sql.Connection con) -> {
            try (var lock = con.prepareStatement("select pg_try_advisory_lock(?)")) {
                lock.setLong(1, LOCK_KEY);
                var rs = lock.executeQuery();
                if (!rs.next() || !rs.getBoolean(1)) return null;
            }
            try {
                int n = publisher.runOnce();
                if (n > 0) log.info("Publisher cycle published {} post(s)", n);
            } catch (RuntimeException e) {
                log.warn("Publisher cycle failed: {}", e.getMessage());
            } finally {
                try (var unlock = con.prepareStatement("select pg_advisory_unlock(?)")) {
                    unlock.setLong(1, LOCK_KEY);
                    unlock.execute();
                }
            }
            return null;
        });
    }
}
