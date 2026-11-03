package com.ai.xbot.dto;

import com.ai.xbot.entity.enums.PostSlot;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * @param slot        which daily slot the draft is for (decides theme and proposed time)
 * @param language    ta / en / mixed; null = pick from the configured language mix
 * @param topic       optional topic or angle
 * @param sourceNotes optional source material (e.g. key points from an article or press release).
 *                    Without it, the AI writes a discussion post with no factual claims.
 * @param sourceUrls  optional links to the material; the only URLs a draft may cite
 */
public record GenerateDraftRequest(
        @NotNull PostSlot slot,
        @Pattern(regexp = "ta|en|mixed", message = "must be ta, en or mixed") String language,
        @Size(max = 300) String topic,
        @Size(max = 8000) String sourceNotes,
        @Size(max = 5, message = "at most 5 source URLs") List<@Size(max = 1000) String> sourceUrls) {
}
