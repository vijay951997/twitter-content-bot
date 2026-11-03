package com.ai.xbot.dto;

import jakarta.validation.constraints.NotNull;

public record PublishingSettingsDto(@NotNull Boolean enabled) {
}
