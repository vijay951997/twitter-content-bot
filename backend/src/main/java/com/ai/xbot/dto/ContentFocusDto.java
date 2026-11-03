package com.ai.xbot.dto;

import jakarta.validation.constraints.*;
import java.util.List;

/** Drafting perspective and news focus. Honesty rules in the system prompt apply regardless of the voice. */
public record ContentFocusDto(
        @NotBlank @Size(max = 1500) String voice,
        @NotBlank @Size(max = 300) String themeMorning,
        @NotBlank @Size(max = 300) String themeAfternoon,
        @NotBlank @Size(max = 300) String themeEvening,
        @NotNull @Size(max = 15) List<@Pattern(regexp = "#[\\p{L}\\p{M}\\p{N}_]{1,50}", message = "must look like #Word") String> hashtags,
        @NotNull @Min(0) @Max(5) Integer maxHashtags,
        @NotNull @Size(max = 40) List<@Size(min = 2, max = 60) String> focusKeywords,
        boolean focusOnly) {
}
