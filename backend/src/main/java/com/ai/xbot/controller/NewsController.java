package com.ai.xbot.controller;

import com.ai.xbot.dto.*;
import com.ai.xbot.entity.enums.AnalysisStatus;
import com.ai.xbot.news.ArticleAnalysisService;
import com.ai.xbot.news.NewsDiscoveryService;
import com.ai.xbot.news.NewsQueryService;
import com.ai.xbot.news.SourceRegistryService;
import com.ai.xbot.security.AuthenticatedUser;
import com.ai.xbot.service.drafts.DraftGenerationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final SourceRegistryService registry;
    private final NewsDiscoveryService discovery;
    private final ArticleAnalysisService analysis;
    private final NewsQueryService queries;
    private final DraftGenerationService drafts;

    public NewsController(SourceRegistryService registry, NewsDiscoveryService discovery, ArticleAnalysisService analysis,
                          NewsQueryService queries, DraftGenerationService drafts) {
        this.registry = registry;
        this.discovery = discovery;
        this.analysis = analysis;
        this.queries = queries;
        this.drafts = drafts;
    }

    // ---- sources ----
    @GetMapping("/sources")
    public List<NewsSourceDto> sources() { return registry.list(); }

    @PostMapping("/sources")
    public NewsSourceDto create(@Valid @RequestBody NewsSourceRequest body, @AuthenticationPrincipal AuthenticatedUser u) {
        return registry.create(body, u.getId());
    }

    @PutMapping("/sources/{id}")
    public NewsSourceDto update(@PathVariable UUID id, @Valid @RequestBody NewsSourceRequest body,
                                @AuthenticationPrincipal AuthenticatedUser u) {
        return registry.update(id, body, u.getId());
    }

    @PostMapping("/sources/{id}/fetch")
    public FetchRunDto fetchSource(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser u) {
        return discovery.fetchSource(id, u.getId());
    }

    @PostMapping("/fetch")
    public FetchRunDto fetchAll(@AuthenticationPrincipal AuthenticatedUser u) {
        return discovery.fetchAll(false, u.getId());
    }

    // ---- articles ----
    @GetMapping("/articles")
    public PageDto<ArticleDto> articles(@RequestParam(required = false) AnalysisStatus status,
                                        @RequestParam(defaultValue = "true") boolean relevantOnly,
                                        @RequestParam(required = false) UUID sourceId,
                                        @RequestParam(defaultValue = "false") boolean focusOnly,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return queries.list(status, relevantOnly, sourceId, focusOnly, page, size);
    }

    @GetMapping("/articles/{id}")
    public ArticleDto article(@PathVariable UUID id) { return queries.get(id); }

    @PostMapping("/articles/{id}/analyse")
    public ArticleDto analyse(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser u) {
        analysis.analyseAndVerify(id, u.getId());
        return queries.get(id);
    }

    @PostMapping("/articles/{id}/verify")
    public ArticleDto verify(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser u) {
        analysis.verify(id, u.getId());
        return queries.get(id);
    }

    @PostMapping("/articles/{id}/draft")
    public GenerationResultDto draft(@PathVariable UUID id, @Valid @RequestBody(required = false) DraftFromArticleRequest body,
                                     @AuthenticationPrincipal AuthenticatedUser u) {
        return drafts.generateFromArticle(id, body == null ? null : body.slot(), body == null ? null : body.language(), u.getId());
    }
}
