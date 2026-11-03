package com.ai.xbot.news;

import com.ai.xbot.audit.AuditService;
import com.ai.xbot.dto.FetchRunDto;
import com.ai.xbot.dto.FetchRunDto.SourceOutcome;
import com.ai.xbot.entity.NewsArticle;
import com.ai.xbot.entity.NewsSource;
import com.ai.xbot.entity.enums.ActorType;
import com.ai.xbot.exception.NotFoundException;
import com.ai.xbot.integration.news.FeedClient;
import com.ai.xbot.integration.news.FeedItem;
import com.ai.xbot.integration.news.FeedResult;
import com.ai.xbot.repository.NewsArticleRepository;
import com.ai.xbot.repository.NewsSourceRepository;
import com.ai.xbot.service.ContentHasher;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fetches enabled sources and stores new articles (metadata only). Network calls happen outside
 * database transactions. Feed excerpts are kept in memory for a short time so analysis can use them,
 * but are never written to the database.
 */
@Service
public class NewsDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(NewsDiscoveryService.class);

    private final NewsSourceRepository sources;
    private final NewsArticleRepository articles;
    private final FeedClient feeds;
    private final ContentHasher hasher;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final com.ai.xbot.service.SettingsService settings;

    /** articleId -> excerpt, kept briefly for analysis. Bounded; lost on restart (analysis then uses the title). */
    private final Map<UUID, String> excerpts = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<UUID, String> eldest) { return size() > 2000; }
    });
    private final Set<UUID> inProgress = ConcurrentHashMap.newKeySet();

    public NewsDiscoveryService(NewsSourceRepository sources, NewsArticleRepository articles, FeedClient feeds,
                                ContentHasher hasher, AuditService audit, TransactionTemplate tx, Clock clock,
                                com.ai.xbot.service.SettingsService settings) {
        this.settings = settings;
        this.sources = sources;
        this.articles = articles;
        this.feeds = feeds;
        this.hasher = hasher;
        this.audit = audit;
        this.tx = tx;
        this.clock = clock;
    }

    /** Fetches every enabled source (manual run) or only those that are due (scheduled run). */
    public FetchRunDto fetchAll(boolean onlyDue, UUID actor) {
        Instant now = Instant.now(clock);
        List<NewsSource> targets = sources.findByEnabledTrue().stream()
                .filter(s -> s.getFeedUrl() != null && (!onlyDue || s.isDue(now))).toList();
        List<SourceOutcome> outcomes = new ArrayList<>();
        int created = 0, relevant = 0;
        for (NewsSource s : targets) {
            int[] counts = fetchOne(s);
            created += counts[0];
            relevant += counts[1];
            NewsSource refreshed = sources.findById(s.getId()).orElse(s);
            outcomes.add(new SourceOutcome(s.getName(), refreshed.getLastFetchStatus(), refreshed.getLastFetchMessage(), counts[0]));
        }
        if (!targets.isEmpty()) {
            int c = created, r = relevant;
            tx.executeWithoutResult(st -> audit.record(actor, actor == null ? ActorType.SCHEDULER : ActorType.USER,
                    "NEWS_FETCH_RUN", "news_sources", null, null,
                    Map.of("sources", targets.size(), "newArticles", c, "relevant", r), null));
        }
        return new FetchRunDto(targets.size(), created, relevant, outcomes);
    }

    public FetchRunDto fetchSource(UUID sourceId, UUID actor) {
        NewsSource s = sources.findById(sourceId).orElseThrow(() -> new NotFoundException("Source not found"));
        if (s.getFeedUrl() == null) {
            throw new com.ai.xbot.exception.BadRequestException("This source has no feed. Paste its releases in Drafts with their link.");
        }
        int[] counts = fetchOne(s);
        NewsSource refreshed = sources.findById(sourceId).orElse(s);
        return new FetchRunDto(1, counts[0], counts[1],
                List.of(new SourceOutcome(s.getName(), refreshed.getLastFetchStatus(), refreshed.getLastFetchMessage(), counts[0])));
    }

    /** @return {new articles, relevant new articles} */
    int[] fetchOne(NewsSource source) {
        if (!inProgress.add(source.getId())) return new int[]{0, 0};
        try {
            FeedResult result = feeds.fetch(source.getFeedUrl(), source.getEtag(), source.getLastModified());
            var focus = settings.getFocus();
            return tx.execute(st -> {
                NewsSource s = sources.findById(source.getId()).orElseThrow();
                int created = 0, relevant = 0;
                if (result.status() == FeedResult.Status.OK) {
                    for (FeedItem item : result.items()) {
                        String url = UrlNormalizer.normalize(item.link());
                        if (articles.existsByOriginalUrl(url)) continue;
                        RelevanceFilter.Result rel = RelevanceFilter.evaluate(item.title(), item.excerpt(), s.isRequiresKeywordMatch());
                        boolean focusMatch = RelevanceFilter.matchesFocus(item.title(), item.excerpt(), focus.focusKeywords());
                        boolean keep = rel.relevant() && (!focus.focusOnly() || focusMatch);
                        NewsArticle article = new NewsArticle(s.getId(), item.title(), url, item.publishedAt(),
                                Instant.now(clock), s.getLanguage(), hasher.hash(item.title()), keep,
                                rel.matched(), rel.category());
                        article.setFocusMatch(focusMatch);
                        NewsArticle a = articles.save(article);
                        if (item.excerpt() != null && !item.excerpt().isBlank()) excerpts.put(a.getId(), item.excerpt());
                        created++;
                        if (a.isRelevant()) relevant++;
                    }
                }
                String message = result.status() == FeedResult.Status.OK
                        ? result.items().size() + " items, " + created + " new (" + relevant + " relevant)"
                        : result.message();
                s.recordFetch(Instant.now(clock), result.status().name(), message, result.success(),
                        result.etag(), result.lastModified());
                log.info("Fetched source='{}' status={} new={} relevant={}", s.getName(), result.status(), created, relevant);
                return new int[]{created, relevant};
            });
        } finally {
            inProgress.remove(source.getId());
        }
    }

    /** Publisher excerpt for an article if it is still in memory; never persisted. */
    public Optional<String> excerptFor(UUID articleId) {
        return Optional.ofNullable(excerpts.get(articleId));
    }

    public void forgetExcerpt(UUID articleId) {
        excerpts.remove(articleId);
    }
}
