package com.ai.xbot.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RejectDraftRequest(@NotNull Long version, @Size(max = 500) String reason) {
}
