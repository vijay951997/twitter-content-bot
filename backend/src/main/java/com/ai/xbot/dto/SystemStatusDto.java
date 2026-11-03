package com.ai.xbot.dto;

/** Reports whether integrations are configured. Never contains secret values. */
public record SystemStatusDto(
        String version,
        String timezone,
        boolean openAiConfigured,
        String openAiModel,
        boolean xApiConfigured,
        boolean xAccountConnected,
        boolean publishingEnabled,
        String phase) {
}
