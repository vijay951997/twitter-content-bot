package com.ai.xbot.controller;

import com.ai.xbot.dto.NotificationDto;
import com.ai.xbot.repository.NotificationLogRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationLogRepository repository;
    private final Clock clock;

    public NotificationController(NotificationLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "20") int limit) {
        List<NotificationDto> items = repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.max(1, Math.min(limit, 100))))
                .stream().map(n -> new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                        n.getRelatedEntityType(), n.getRelatedEntityId(), n.getReadAt() != null, n.getCreatedAt()))
                .toList();
        return Map.of("unread", repository.countByReadAtIsNull(), "items", items);
    }

    @PostMapping("/read-all")
    @Transactional
    public Map<String, Object> readAll() {
        Instant now = Instant.now(clock);
        repository.findByReadAtIsNull().forEach(n -> n.markRead(now));
        return Map.of("unread", 0);
    }
}
