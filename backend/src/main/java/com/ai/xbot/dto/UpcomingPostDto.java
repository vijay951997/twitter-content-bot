package com.ai.xbot.dto;

import java.time.Instant;
import java.util.UUID;

public record UpcomingPostDto(UUID id, String preview, String language, String category, Instant publishAt) {
}
