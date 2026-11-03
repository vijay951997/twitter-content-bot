package com.ai.xbot.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationDto(UUID id, String type, String title, String message, String entityType, UUID entityId,
                              boolean read, Instant createdAt) {
}
