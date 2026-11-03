package com.ai.xbot.controller;

import com.ai.xbot.dto.DraftDto;
import com.ai.xbot.dto.GenerateDraftRequest;
import com.ai.xbot.dto.GenerationResultDto;
import com.ai.xbot.dto.PageDto;
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

    public DraftController(DraftGenerationService generation, DraftQueryService queries) {
        this.generation = generation;
        this.queries = queries;
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
