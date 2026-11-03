package com.ai.xbot.news;

import com.ai.xbot.dto.ArticleDto;
import com.ai.xbot.dto.ClaimDto;
import com.ai.xbot.dto.PageDto;
import com.ai.xbot.entity.FactualClaim;
import com.ai.xbot.entity.NewsArticle;
import com.ai.xbot.entity.NewsSource;
import com.ai.xbot.entity.enums.AnalysisStatus;
import com.ai.xbot.exception.NotFoundException;
import com.ai.xbot.repository.FactualClaimRepository;
import com.ai.xbot.repository.NewsArticleRepository;
import com.ai.xbot.repository.NewsSourceRepository;
import com.ai.xbot.repository.PostSourceRepository;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NewsQueryService {

    private final NewsArticleRepository articles;
    private final NewsSourceRepository sources;
    private final FactualClaimRepository claims;
    private final PostSourceRepository postSources;

    public NewsQueryService(NewsArticleRepository articles, NewsSourceRepository sources,
                            FactualClaimRepository claims, PostSourceRepository postSources) {
        this.articles = articles;
        this.sources = sources;
        this.claims = claims;
        this.postSources = postSources;
    }

    @Transactional(readOnly = true)
    public PageDto<ArticleDto> list(AnalysisStatus status, boolean relevantOnly, UUID sourceId, boolean focusOnly, int page, int size) {
        Page<NewsArticle> p = articles.search(status, relevantOnly, sourceId, focusOnly,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 50))));
        List<UUID> ids = p.getContent().stream().map(NewsArticle::getId).toList();
        Map<UUID, List<FactualClaim>> claimMap = ids.isEmpty() ? Map.of() : claims.findByArticleIdIn(ids).stream()
                .collect(Collectors.groupingBy(FactualClaim::getArticleId));
        Set<UUID> used = ids.isEmpty() ? Set.of() : postSources.findArticleIdsUsed(ids);
        Map<UUID, NewsSource> srcs = sources.findAll().stream().collect(Collectors.toMap(NewsSource::getId, s -> s));
        List<ArticleDto> items = p.getContent().stream()
                .map(a -> toDto(a, srcs.get(a.getSourceId()), claimMap.getOrDefault(a.getId(), List.of()), used.contains(a.getId())))
                .toList();
        return new PageDto<>(items, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ArticleDto get(UUID id) {
        NewsArticle a = articles.findById(id).orElseThrow(() -> new NotFoundException("Article not found"));
        return toDto(a, sources.findById(a.getSourceId()).orElse(null), claims.findByArticleIdOrderByCreatedAtAsc(id),
                !postSources.findArticleIdsUsed(List.of(id)).isEmpty());
    }

    static ArticleDto toDto(NewsArticle a, NewsSource s, List<FactualClaim> cs, boolean used) {
        List<FactualClaim> ordered = new ArrayList<>(cs);
        ordered.sort(Comparator.comparing(FactualClaim::getId));
        List<ClaimDto> claimDtos = ordered.stream().map(c -> new ClaimDto(c.getId(), c.getClaimText(), c.getClaimType().name(),
                c.getAttributedTo(), c.getVerificationStatus().name(), c.getVerificationMethod(),
                c.getIndependentSourceCount(), c.getSupportingUrls(), c.getConflictingUrls(), c.getVerificationNotes(),
                c.getVerifiedAt())).toList();
        return new ArticleDto(a.getId(), a.getTitle(), a.getOriginalUrl(), a.getSourceId(),
                s == null ? "?" : s.getName(), s == null ? null : s.getSourceType().name(),
                s != null && s.isPrimarySource(), s == null ? 3 : s.getReliabilityTier(), a.getPublishedAt(),
                a.getRetrievedAt(), a.getSummary(), a.getTopicCategory(), a.getLanguage() == null ? null : a.getLanguage().code(),
                a.getAnalysisStatus().name(), a.isRelevant(), a.getMatchedKeywords(), a.getAnalysisError(),
                a.getAnalysedAt(), a.getVerifiedAt(), claimDtos, used, a.isFocusMatch());
    }
}
