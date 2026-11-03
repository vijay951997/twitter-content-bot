package com.ai.xbot.dto;

import java.time.Instant;
import java.util.UUID;

public record NewsSourceDto(UUID id, String name, String baseUrl, String feedUrl, String sourceType, String language,
                            boolean primarySource, int reliabilityTier, String reliabilityNotes, boolean enabled,
                            int fetchIntervalMinutes, boolean requiresKeywordMatch, Instant lastFetchedAt,
                            String lastFetchStatus, String lastFetchMessage, int consecutiveFailures) {
}
