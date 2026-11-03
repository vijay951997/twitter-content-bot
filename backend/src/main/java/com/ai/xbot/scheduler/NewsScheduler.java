package com.ai.xbot.scheduler;

import com.ai.xbot.news.ArticleAnalysisService;
import com.ai.xbot.news.NewsDiscoveryService;
import com.ai.xbot.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background news fetching, off by default (setting news.auto_fetch_enabled). A PostgreSQL advisory
 * lock ensures only one application instance runs a cycle at a time. Never generates or publishes posts.
 */
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class NewsScheduler {

    private static final Logger log = LoggerFactory.getLogger(NewsScheduler.class);
    static final long LOCK_KEY = 0x58424f54_4e455753L; // "XBOTNEWS"

    private final NewsDiscoveryService discovery;
    private final ArticleAnalysisService analysis;
    private final SettingsService settings;
    private final JdbcTemplate jdbc;

    public NewsScheduler(NewsDiscoveryService discovery, ArticleAnalysisService analysis, SettingsService settings,
                         JdbcTemplate jdbc) {
        this.discovery = discovery;
        this.analysis = analysis;
        this.settings = settings;
        this.jdbc = jdbc;
    }

    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT5M")
    public void run() {
        if (!settings.flag("news.auto_fetch_enabled")) return;
        jdbc.execute((java.sql.Connection con) -> {
            try (var lock = con.prepareStatement("select pg_try_advisory_lock(?)")) {
                lock.setLong(1, LOCK_KEY);
                var rs = lock.executeQuery();
                if (!rs.next() || !rs.getBoolean(1)) {
                    log.debug("News cycle already running on another instance");
                    return null;
                }
            }
            try {
                var result = discovery.fetchAll(true, null);
                int analysed = 0;
                if (settings.flag("news.auto_analyse_enabled") && result.relevantArticles() > 0) {
                    int max = Integer.parseInt(settings.rawValue("news.max_auto_analyses_per_run").orElse("5"));
                    analysed = analysis.analyseNewest(max);
                }
                if (result.sourcesChecked() > 0) {
                    log.info("News cycle: sources={} new={} relevant={} analysed={}", result.sourcesChecked(),
                            result.newArticles(), result.relevantArticles(), analysed);
                }
            } catch (RuntimeException e) {
                log.warn("News cycle failed: {}", e.getMessage());
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
