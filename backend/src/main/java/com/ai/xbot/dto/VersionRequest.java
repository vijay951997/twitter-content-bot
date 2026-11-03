package com.ai.xbot.dto;

import jakarta.validation.constraints.NotNull;

public record VersionRequest(@NotNull Long version) {
}
