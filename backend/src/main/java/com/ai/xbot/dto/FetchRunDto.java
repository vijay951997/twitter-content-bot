package com.ai.xbot.dto;

import java.util.List;

public record FetchRunDto(int sourcesChecked, int newArticles, int relevantArticles, List<SourceOutcome> sources) {
    public record SourceOutcome(String source, String status, String message, int newArticles) {}
}
