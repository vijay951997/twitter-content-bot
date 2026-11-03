package com.ai.xbot.dto;

import com.ai.xbot.entity.enums.SourceType;
import jakarta.validation.constraints.*;

public record NewsSourceRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 500) String baseUrl,
        @Size(max = 500) String feedUrl,   // optional: manual sources (e.g. TN DIPR) have no feed
        @NotNull SourceType sourceType,
        @NotBlank @Pattern(regexp = "ta|en|mixed") String language,
        boolean primarySource,
        @Min(1) @Max(3) int reliabilityTier,
        @Size(max = 1000) String reliabilityNotes,
        boolean enabled,
        @Min(15) @Max(1440) int fetchIntervalMinutes,
        boolean requiresKeywordMatch) {
}
