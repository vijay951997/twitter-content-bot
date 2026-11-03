package com.ai.xbot.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ArticleDto(UUID id, String title, String url, UUID sourceId, String sourceName, String sourceType,
                         boolean primarySource, int reliabilityTier, Instant publishedAt, Instant retrievedAt,
                         String summary, String category, String language, String analysisStatus, boolean relevant,
                         List<String> matchedKeywords, String analysisError, Instant analysedAt, Instant verifiedAt,
                         List<ClaimDto> claims, boolean usedInDraft, boolean focusMatch) {
}
