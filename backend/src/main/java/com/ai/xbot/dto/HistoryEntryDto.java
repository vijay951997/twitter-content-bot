package com.ai.xbot.dto;

import java.time.Instant;

public record HistoryEntryDto(Instant at, String action, String actor, String details, String oldValue, String newValue) {
}
