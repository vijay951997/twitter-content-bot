package com.ai.xbot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * @param enabledLanguages language codes: ta, en, mixed
 * @param languageMix      percentage per enabled language; must total 100
 */
public record ContentSettingsDto(
        @NotEmpty List<String> enabledLanguages,
        @NotEmpty Map<String, Integer> languageMix,
        @NotNull @Min(1) @Max(5) Integer variationsPerTopic,
        @NotNull @Min(1) @Max(365) Integer duplicateLookbackDays) {
}
