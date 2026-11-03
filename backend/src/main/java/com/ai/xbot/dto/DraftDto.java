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
        UUID variationGroupId,
        long version,
        boolean editedByHuman,
        boolean savedForLater,
        String rejectionReason,
        UUID parentPostId,
        Publishing publishing) {

    /** Scheduling / publishing state, when the draft has been approved. */
    public record Publishing(String queueStatus, Instant scheduledAt, int attempts, String lastError,
                             String xPostId, String xUrl, Instant publishedAt, String approvedBy, Instant approvedAt) {}

    public DraftDto withPublishing(Publishing p) {
        return new DraftDto(id, content, language, category, slot, status, verificationStatus, qualityEstimate,
                proposedPublishAt, createdAt, topic, sourceNotes, sourceUrls, verifiedClaims, unverifiedClaims, riskFlags,
                duplicateWarning, weightedLength, maxLength, aiModel, promptVersion, variationGroupId, version,
                editedByHuman, savedForLater, rejectionReason, parentPostId, p);
    }
}
