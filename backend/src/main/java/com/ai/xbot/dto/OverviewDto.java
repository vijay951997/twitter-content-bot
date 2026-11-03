package com.ai.xbot.dto;

import java.util.List;
import java.util.Map;

public record OverviewDto(
        long totalDrafts,
        long pendingApprovals,
        long approved,
        long scheduled,
        long published,
        long rejected,
        long failed,
        Map<String, Long> byStatus,
        List<UpcomingPostDto> upcoming) {
}
