package com.ai.xbot.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Full draft view for the review dashboard. qualityEstimate is the AI's own estimate, not a measurement. */
public record DraftDto(
        UUID id,
        String content,
        String language,
        String category,
        String slot,
        String status,
        String verificationStatus,
        BigDecimal qualityEstimate,
        Instant proposedPublishAt,
        Instant createdAt,
        String topic,
        String sourceNotes,
        List<String> sourceUrls,
        List<String> verifiedClaims,
        List<String> unverifiedClaims,
        List<String> riskFlags,
        String duplicateWarning,
        Integer weightedLength,
        int maxLength,
        String aiModel,
        String promptVersion,
        UUID variationGroupId) {
}
