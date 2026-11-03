package com.ai.xbot.controller;

import com.ai.xbot.dto.AuditLogDto;
import com.ai.xbot.repository.AuditLogRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditLogRepository repository;

    public AuditController(AuditLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<AuditLogDto> recent(@RequestParam(defaultValue = "50") int limit) {
        int size = Math.max(1, Math.min(limit, 200));
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, size)).stream()
                .map(a -> new AuditLogDto(a.getId(), a.getActorType().name(), a.getActorId(), a.getAction(),
                        a.getEntityType(), a.getEntityId(), a.getOldValue(), a.getNewValue(),
                        a.getDetails(), a.getCreatedAt()))
                .toList();
    }
}
