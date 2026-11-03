package com.ai.xbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** @param version the draft version the user edited (optimistic locking) */
public record EditDraftRequest(@NotBlank @Size(max = 2000) String content, @NotNull Long version) {
}
