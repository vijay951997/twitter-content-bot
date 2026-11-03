package com.ai.xbot.service.drafts;

import com.ai.xbot.dto.DraftDto;
import com.ai.xbot.entity.GeneratedPost;
import com.ai.xbot.service.text.XTextLength;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DraftMapper {

    private final ObjectMapper objectMapper;

    public DraftMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DraftDto toDto(GeneratedPost p, List<String> sourceUrls) {
        List<String> verified = new ArrayList<>();
        List<String> unverified = new ArrayList<>();
        if (p.getAiClaims() != null) {
            try {
                JsonNode n = objectMapper.readTree(p.getAiClaims());
                n.path("verified").forEach(v -> verified.add(v.asText()));
                n.path("unverified").forEach(v -> unverified.add(v.asText()));
            } catch (Exception ignored) {
                // Claims are a review aid; a malformed value must not hide the draft.
            }
        }
        return new DraftDto(p.getId(), p.getContent(), p.getLanguage().code(), p.getCategory(),
                p.getSlot() == null ? null : p.getSlot().name(), p.getStatus().name(), p.getVerificationStatus().name(),
                p.getQualityEstimate(), p.getProposedPublishAt(), p.getCreatedAt(), p.getTopic(), p.getSourceNotes(),
                sourceUrls, verified, unverified, p.getRiskFlags(), p.getDuplicateWarning(),
                p.getWeightedLength() != null ? p.getWeightedLength() : XTextLength.weightedLength(p.getContent()),
                XTextLength.MAX_WEIGHTED_LENGTH, p.getAiModel(), p.getPromptVersion(), p.getVariationGroupId(),
                p.getVersion(), p.isEditedByHuman(), p.isSavedForLater(), p.getRejectionReason(), p.getParentPostId(), null);
    }
}
