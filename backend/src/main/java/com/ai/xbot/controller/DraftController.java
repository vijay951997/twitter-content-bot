package com.ai.xbot.controller;

import com.ai.xbot.dto.*;
import com.ai.xbot.audit.AuditService;
import com.ai.xbot.repository.AuditLogRepository;
import com.ai.xbot.repository.UserRepository;
import com.ai.xbot.service.review.ReviewService;
import java.util.List;
import com.ai.xbot.entity.enums.PostStatus;
import com.ai.xbot.security.AuthenticatedUser;
import com.ai.xbot.service.drafts.DraftGenerationService;
import com.ai.xbot.service.drafts.DraftQueryService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/drafts")
public class DraftController {

    private final DraftGenerationService generation;
    private final DraftQueryService queries;
    private final ReviewService review;
    private final AuditLogRepository auditLogs;
    private final UserRepository users;

    public DraftController(DraftGenerationService generation, DraftQueryService queries, ReviewService review,
                           AuditLogRepository auditLogs, UserRepository users) {
        this.generation = generation;
        this.queries = queries;
        this.review = review;
        this.auditLogs = auditLogs;
        this.users = users;
    }

    @PutMapping("/{id}")
    public DraftDto edit(@PathVariable UUID id, @Valid @RequestBody EditDraftRequest body,
                         @AuthenticationPrincipal AuthenticatedUser user) {
        return review.edit(id, body, user.getId());
    }

    @PostMapping("/{id}/approve")
    public DraftDto approve(@PathVariable UUID id, @Valid @RequestBody ApproveDraftRequest body,
                            @AuthenticationPrincipal AuthenticatedUser user) {
        return review.approve(id, body, user.getId());
    }

    @PostMapping("/{id}/reject")
    public DraftDto reject(@PathVariable UUID id, @Valid @RequestBody RejectDraftRequest body,
                           @AuthenticationPrincipal AuthenticatedUser user) {
        return review.reject(id, body, user.getId());
    }

    @PostMapping("/{id}/unschedule")
    public DraftDto unschedule(@PathVariable UUID id, @Valid @RequestBody VersionRequest body,
                               @AuthenticationPrincipal AuthenticatedUser user) {
        return review.unschedule(id, body.version(), user.getId());
    }

    @PostMapping("/{id}/retry")
    public DraftDto retry(@PathVariable UUID id, @Valid @RequestBody VersionRequest body,
                          @AuthenticationPrincipal AuthenticatedUser user) {
        return review.retryPublish(id, body.version(), user.getId());
    }

    @PostMapping("/{id}/cancel")
    public DraftDto cancel(@PathVariable UUID id, @Valid @RequestBody VersionRequest body,
                           @AuthenticationPrincipal AuthenticatedUser user) {
        return review.cancelFailed(id, body.version(), user.getId());
    }

    @PostMapping("/{id}/save-for-later")
    public DraftDto saveForLater(@PathVariable UUID id, @RequestParam(defaultValue = "true") boolean saved,
                                 @AuthenticationPrincipal AuthenticatedUser user) {
        return review.saveForLater(id, saved, user.getId());
    }

    @PostMapping("/{id}/regenerate")
    public GenerationResultDto regenerate(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return generation.regenerate(id, user.getId());
    }

    /** Audit history of one draft: who changed what, and when. */
    @GetMapping("/{id}/history")
    public List<HistoryEntryDto> history(@PathVariable UUID id) {
        queries.get(id); // 404 if missing
        var names = new java.util.HashMap<UUID, String>();
        users.findAll().forEach(u -> names.put(u.getId(), u.getDisplayName() != null ? u.getDisplayName() : u.getUsername()));
        return auditLogs.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("generated_posts", id).stream()
                .map(a -> new HistoryEntryDto(a.getCreatedAt(), a.getAction(),
                        a.getActorId() != null ? names.getOrDefault(a.getActorId(), "user") : a.getActorType().name().toLowerCase(),
                        a.getDetails(), a.getOldValue(), a.getNewValue()))
                .toList();
    }

    @PostMapping("/generate")
    public GenerationResultDto generate(@Valid @RequestBody GenerateDraftRequest body,
                                        @AuthenticationPrincipal AuthenticatedUser user) {
        return generation.generate(body, user.getId());
    }

    @PostMapping("/generate-daily")
    public GenerationResultDto generateDaily(@AuthenticationPrincipal AuthenticatedUser user) {
        return generation.generateDaily(user.getId());
    }

    @GetMapping
    public PageDto<DraftDto> list(@RequestParam(required = false) PostStatus status,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        return queries.list(status, page, size);
    }

    @GetMapping("/{id}")
    public DraftDto get(@PathVariable UUID id) {
        return queries.get(id);
    }
}
