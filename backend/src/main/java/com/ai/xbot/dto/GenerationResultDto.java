package com.ai.xbot.dto;

import java.util.List;

/**
 * @param created    drafts saved for review (status GENERATED)
 * @param discarded  AI outputs that failed a check and were not saved, with the reason
 */
public record GenerationResultDto(List<DraftDto> created, List<Discarded> discarded) {

    public record Discarded(String slot, String reason, String preview) {}
}
