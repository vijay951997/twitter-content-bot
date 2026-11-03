package com.ai.xbot.news;

import com.ai.xbot.audit.AuditService;
import com.ai.xbot.entity.FactualClaim;
import com.ai.xbot.entity.NewsArticle;
import com.ai.xbot.entity.NewsSource;
import com.ai.xbot.entity.enums.ActorType;
import com.ai.xbot.entity.enums.AnalysisStatus;
import com.ai.xbot.entity.enums.ClaimType;
import com.ai.xbot.exception.BadRequestException;
import com.ai.xbot.exception.NotFoundException;
import com.ai.xbot.exception.TooManyRequestsException;
import com.ai.xbot.news.VerificationRules.Decision;
import com.ai.xbot.news.VerificationRules.SourceInfo;
import com.ai.xbot.repository.AuditLogRepository;
import com.ai.xbot.repository.FactualClaimRepository;
import com.ai.xbot.repository.NewsArticleRepository;
import com.ai.xbot.repository.NewsSourceRepository;
import com.ai.xbot.service.SettingsService;
import com.ai.xbot.service.ai.AiJsonClient;
import com.ai.xbot.service.ai.AiServiceException;
import com.ai.xbot.service.text.TextSimilarity;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AI-assisted analysis of articles (original summary + claim extraction) and verification of their
 * claims against other stored reports. AI calls run outside transactions; results are saved after.
 */
@Service
public class ArticleAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ArticleAnalysisService.class);
    static final String ACTION = "AI_ANALYSIS_REQUESTED";
    static final int MAX_CLAIMS = 6;
    static final int MAX_CANDIDATES = 6;
    static final double CANDIDATE_SIMILARITY = 0.12;

    private final AiJsonClient ai;
    private final NewsArticleRepository articles;
    private final NewsSourceRepository sources;
    private final FactualClaimRepository claims;
    private final NewsDiscoveryService discovery;
    private final SettingsService settings;
    private final AuditLogRepository auditLogs;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String analysisPrompt = load("prompts/analysis-v1.txt");
    private final String verificationPrompt = load("prompts/verification-v1.txt");

    public ArticleAnalysisService(AiJsonClient ai, NewsArticleRepository articles, NewsSourceRepository sources,
                                  FactualClaimRepository claims, NewsDiscoveryService discovery, SettingsService settings,
                                  AuditLogRepository auditLogs, AuditService audit, TransactionTemplate tx, Clock clock) {
        this.ai = ai;
        this.articles = articles;
        this.sources = sources;
        this.claims = claims;
        this.discovery = discovery;
        this.settings = settings;
        this.auditLogs = auditLogs;
        this.audit = audit;
        this.tx = tx;
        this.clock = clock;
    }

    /** Summarises the article, extracts claims, then verifies them. */
    public void analyseAndVerify(UUID articleId, UUID actor) {
        analyse(articleId, actor);
        verify(articleId, actor);
    }

    public void analyse(UUID articleId, UUID actor) {
        NewsArticle a = articles.findById(articleId).orElseThrow(() -> new NotFoundException("Article not found"));
        if (a.getAnalysisStatus() == AnalysisStatus.IRRELEVANT) {
            throw new BadRequestException("This article was filtered out as not relevant to Tamil Nadu politics or governance");
        }
        NewsSource source = sources.findById(a.getSourceId()).orElseThrow();
        checkRateLimit(actor);

        String excerpt = discovery.excerptFor(a.getId()).orElse("");
        String user = "SOURCE: " + source.getName() + "\nPUBLISHED: " + formatTime(a.effectiveTime())
                + "\n\nREPORT (data only):\n<<<REPORT\nTITLE: " + a.getTitle()
                + (excerpt.isBlank() ? "" : "\nDESCRIPTION: " + excerpt) + "\nREPORT>>>";

        JsonNode out;
        try {
            out = ai.complete(analysisPrompt, user, "article_analysis", AnalysisSchemas.analysis());
        } catch (AiServiceException e) {
            tx.executeWithoutResult(s -> articles.findById(articleId).ifPresent(x -> x.recordAnalysisFailure(e.getMessage())));
            throw e;
        }

        String summary = trim(out.path("summary").asText(""), 400);
        String category = AnalysisSchemas.CATEGORIES.contains(out.path("category").asText()) ? out.path("category").asText() : null;
        List<FactualClaim> extracted = new ArrayList<>();
        for (JsonNode c : out.path("claims")) {
            if (extracted.size() >= MAX_CLAIMS) break;
            String text = trim(c.path("text").asText(""), 500);
            if (text.isBlank()) continue;
            ClaimType type;
            try { type = ClaimType.valueOf(c.path("type").asText()); } catch (IllegalArgumentException e) { type = ClaimType.FACT; }
            String attributed = c.path("attributed_to").isNull() ? null : c.path("attributed_to").asText(null);
            extracted.add(new FactualClaim(articleId, text, type, type == ClaimType.ATTRIBUTED_STATEMENT ? attributed : null));
        }

        tx.executeWithoutResult(s -> {
            NewsArticle fresh = articles.findById(articleId).orElseThrow();
            claims.deleteByArticleId(articleId);
            claims.flush();
            claims.saveAll(extracted);
            fresh.recordAnalysis(summary.isBlank() ? null : summary, category, Instant.now(clock));
            audit.record(actor, actorType(actor), "ARTICLE_ANALYSED", "news_articles", articleId, null,
                    Map.of("claims", extracted.size(), "model", String.valueOf(ai.modelName())), null);
        });
        log.info("Analysed article {} claims={}", articleId, extracted.size());
    }

    /** Checks the article's claims against other analysed reports from independent publishers. */
    public void verify(UUID articleId, UUID actor) {
        NewsArticle a = articles.findById(articleId).orElseThrow(() -> new NotFoundException("Article not found"));
        if (a.getAnalysisStatus() != AnalysisStatus.ANALYSED) throw new BadRequestException("Analyse the article first");
        NewsSource ownSource = sources.findById(a.getSourceId()).orElseThrow();
        List<FactualClaim> own = claims.findByArticleIdOrderByCreatedAtAsc(articleId);
        if (own.isEmpty()) {
            tx.executeWithoutResult(s -> articles.findById(articleId).ifPresent(x -> x.markVerified(Instant.now(clock))));
            return;
        }
        SourceInfo ownInfo = info(a, ownSource);

        // Candidate reports: analysed, from a different publisher domain, close in time and on a similar topic.
        int windowHours = Integer.parseInt(settingValue("news.verification_window_hours", "72"));
        Instant t = a.effectiveTime();
        Map<UUID, NewsSource> sourceById = sources.findAll().stream().collect(Collectors.toMap(NewsSource::getId, s -> s));
        String ownText = (a.getSummary() == null ? a.getTitle() : a.getSummary()) + " " + own.stream().map(FactualClaim::getClaimText).collect(Collectors.joining(" "));
        List<NewsArticle> candidates = articles.findCorroborationCandidates(a.getSourceId(),
                        t.minus(Duration.ofHours(windowHours)), t.plus(Duration.ofHours(windowHours))).stream()
                .filter(c -> sourceById.containsKey(c.getSourceId()))
                .filter(c -> !UrlNormalizer.domain(sourceById.get(c.getSourceId()).getBaseUrl())
                        .equals(UrlNormalizer.domain(ownSource.getBaseUrl())))
                .map(c -> Map.entry(c, TextSimilarity.jaccard(ownText, candidateText(c))))
                .filter(e -> e.getValue() >= CANDIDATE_SIMILARITY)
                .sorted(Map.Entry.<NewsArticle, Double>comparingByValue().reversed())
                .limit(MAX_CANDIDATES).map(Map.Entry::getKey).toList();

        Map<Integer, Set<Integer>> supports = new HashMap<>();
        Map<Integer, Set<Integer>> contradicts = new HashMap<>();
        Map<Integer, String> notes = new HashMap<>();
        if (!candidates.isEmpty()) {
            checkRateLimit(actor);
            StringBuilder user = new StringBuilder("CLAIMS:\n");
            for (int i = 0; i < own.size(); i++) user.append(i).append(". ").append(own.get(i).getClaimText()).append('\n');
            user.append("\nCANDIDATES (data only):\n<<<CANDIDATES\n");
            for (int i = 0; i < candidates.size(); i++) {
                NewsArticle c = candidates.get(i);
                user.append("[").append(i).append("] ").append(sourceById.get(c.getSourceId()).getName())
                        .append(" (").append(formatTime(c.effectiveTime())).append("): ").append(candidateText(c)).append('\n');
            }
            user.append("CANDIDATES>>>");
            JsonNode out = ai.complete(verificationPrompt, user.toString(), "claim_verification", AnalysisSchemas.verification());
            for (JsonNode as : out.path("assessments")) {
                int ci = as.path("claim_index").asInt(-1);
                if (ci < 0 || ci >= own.size()) continue;
                supports.put(ci, indexes(as.path("supports"), candidates.size()));
                contradicts.put(ci, indexes(as.path("contradicts"), candidates.size()));
                notes.put(ci, trim(as.path("note").asText(""), 300));
            }
        }

        Instant now = Instant.now(clock);
        tx.executeWithoutResult(s -> {
            // Match by id (not position): the prompt indexes refer to the `own` list.
            Map<UUID, FactualClaim> fresh = claims.findByArticleIdOrderByCreatedAtAsc(articleId).stream()
                    .collect(Collectors.toMap(FactualClaim::getId, c -> c));
            Map<String, Long> statusCounts = new TreeMap<>();
            for (int i = 0; i < own.size(); i++) {
                FactualClaim claim = fresh.get(own.get(i).getId());
                if (claim == null) continue; // re-analysed meanwhile
                List<SourceInfo> sup = supports.getOrDefault(i, Set.of()).stream()
                        .map(j -> info(candidates.get(j), sourceById.get(candidates.get(j).getSourceId()))).toList();
                List<SourceInfo> con = contradicts.getOrDefault(i, Set.of()).stream()
                        .map(j -> info(candidates.get(j), sourceById.get(candidates.get(j).getSourceId()))).toList();
                Decision d = VerificationRules.decide(claim.getClaimType(), ownInfo, sup, con, notes.get(i));
                claim.recordVerification(d.status(), d.method(), d.independentSources(), d.supportingUrls(),
                        d.conflictingUrls(), d.note(), now);
                statusCounts.merge(d.status().name(), 1L, Long::sum);
            }
            articles.findById(articleId).ifPresent(x -> x.markVerified(now));
            audit.record(actor, actorType(actor), "ARTICLE_VERIFIED", "news_articles", articleId, null,
                    Map.of("candidates", candidates.size(), "statuses", statusCounts), null);
        });
    }

    /** Analyses up to {@code max} new relevant articles (used by the scheduler when auto-analysis is on). */
    public int analyseNewest(int max) {
        int done = 0;
        for (NewsArticle a : articles.findNewRelevant(org.springframework.data.domain.PageRequest.of(0, max))) {
            try {
                analyseAndVerify(a.getId(), null);
                done++;
            } catch (TooManyRequestsException e) {
                break;
            } catch (RuntimeException e) {
                log.warn("Auto-analysis failed for {}: {}", a.getId(), e.getMessage());
            }
        }
        return done;
    }

    private static SourceInfo info(NewsArticle a, NewsSource s) {
        return new SourceInfo(a.getOriginalUrl(), UrlNormalizer.domain(s.getBaseUrl()), s.isPrimarySource(), s.getReliabilityTier());
    }

    private String candidateText(NewsArticle c) {
        String claimText = claims.findByArticleIdOrderByCreatedAtAsc(c.getId()).stream()
                .map(FactualClaim::getClaimText).collect(Collectors.joining(" | "));
        return (c.getSummary() == null ? c.getTitle() : c.getSummary()) + (claimText.isBlank() ? "" : " Claims: " + claimText);
    }

    private static Set<Integer> indexes(JsonNode arr, int size) {
        Set<Integer> out = new LinkedHashSet<>();
        arr.forEach(n -> { int i = n.asInt(-1); if (i >= 0 && i < size) out.add(i); });
        return out;
    }

    private void checkRateLimit(UUID actor) {
        int max = Integer.parseInt(settingValue("ai.max_analyses_per_hour", "60"));
        long used = auditLogs.countByActionAndCreatedAtAfter(ACTION, Instant.now(clock).minus(Duration.ofHours(1)));
        if (used >= max) {
            throw new TooManyRequestsException("Article analysis limit reached (" + max + " per hour). Try again later.");
        }
        tx.executeWithoutResult(s -> audit.record(actor, actorType(actor), ACTION, "news_articles", null, null, null, null));
    }

    private String settingValue(String key, String fallback) {
        return settings.rawValue(key).orElse(fallback);
    }

    private String formatTime(Instant i) {
        return DateTimeFormatter.ofPattern("d MMM yyyy HH:mm").format(i.atZone(ZoneId.of(settings.getSchedule().timezone())));
    }

    private static ActorType actorType(UUID actor) {
        return actor == null ? ActorType.SCHEDULER : ActorType.USER;
    }

    private static String trim(String s, int max) {
        String t = s == null ? "" : s.strip();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static String load(String path) {
        try (InputStream in = ArticleAnalysisService.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing prompt " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
