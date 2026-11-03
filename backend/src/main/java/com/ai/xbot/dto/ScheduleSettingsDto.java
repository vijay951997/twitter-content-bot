package com.ai.xbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ScheduleSettingsDto(
        @NotBlank String timezone,
        @NotBlank @Pattern(regexp = TIME, message = "must be HH:mm (24h)") String morning,
        @NotBlank @Pattern(regexp = TIME, message = "must be HH:mm (24h)") String afternoon,
        @NotBlank @Pattern(regexp = TIME, message = "must be HH:mm (24h)") String evening) {

    public static final String TIME = "^([01]\\d|2[0-3]):[0-5]\\d$";
}
