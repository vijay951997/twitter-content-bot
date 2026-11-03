package com.ai.xbot.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogDto(UUID id, String actorType, UUID actorId, String action, String entityType,
                          UUID entityId, String oldValue, String newValue, String details, Instant createdAt) {
}
