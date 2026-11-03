package com.ai.xbot.service;

import static com.ai.xbot.entity.enums.PostStatus.*;

import com.ai.xbot.dto.OverviewDto;
import com.ai.xbot.dto.UpcomingPostDto;
import com.ai.xbot.entity.GeneratedPost;
import com.ai.xbot.entity.enums.PostStatus;
import com.ai.xbot.repository.GeneratedPostRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private static final int PREVIEW_LENGTH = 140;

    private final GeneratedPostRepository postRepository;
    private final Clock clock;

    public DashboardService(GeneratedPostRepository postRepository, Clock clock) {
        this.postRepository = postRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OverviewDto overview() {
        Map<PostStatus, Long> counts = new EnumMap<>(PostStatus.class);
        for (PostStatus s : PostStatus.values()) counts.put(s, 0L);
        for (Object[] row : postRepository.countGroupedByStatus()) {
            counts.put((PostStatus) row[0], (Long) row[1]);
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        counts.forEach((k, v) -> byStatus.put(k.name(), v));

        List<UpcomingPostDto> upcoming = postRepository
                .findByStatusAndProposedPublishAtAfterOrderByProposedPublishAtAsc(
                        SCHEDULED, Instant.now(clock), PageRequest.of(0, 5))
                .stream().map(DashboardService::toUpcoming).toList();

        return new OverviewDto(total,
                counts.get(GENERATED) + counts.get(UNDER_REVIEW),
                counts.get(APPROVED),
                counts.get(SCHEDULED),
                counts.get(PUBLISHED),
                counts.get(REJECTED),
                counts.get(FAILED),
                byStatus,
                upcoming);
    }

    private static UpcomingPostDto toUpcoming(GeneratedPost p) {
        String content = p.getContent();
        String preview = content.codePointCount(0, content.length()) <= PREVIEW_LENGTH
                ? content
                : content.substring(0, content.offsetByCodePoints(0, PREVIEW_LENGTH)) + "…";
        return new UpcomingPostDto(p.getId(), preview, p.getLanguage().code(), p.getCategory(),
                p.getProposedPublishAt());
    }
}
