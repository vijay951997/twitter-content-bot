package com.ai.xbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import com.ai.xbot.dto.OverviewDto;
import com.ai.xbot.entity.enums.PostStatus;
import com.ai.xbot.repository.GeneratedPostRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock GeneratedPostRepository repository;

    @Test
    void aggregatesCountsAndTreatsGeneratedPlusReviewAsPending() {
        when(repository.countGroupedByStatus()).thenReturn(List.of(
                new Object[]{PostStatus.GENERATED, 2L},
                new Object[]{PostStatus.UNDER_REVIEW, 1L},
                new Object[]{PostStatus.PUBLISHED, 5L},
                new Object[]{PostStatus.FAILED, 1L}));
        when(repository.findByStatusAndProposedPublishAtAfterOrderByProposedPublishAtAsc(any(), any(), any()))
                .thenReturn(List.of());

        DashboardService service = new DashboardService(repository,
                Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC));
        OverviewDto o = service.overview();

        assertThat(o.totalDrafts()).isEqualTo(9);
        assertThat(o.pendingApprovals()).isEqualTo(3);
        assertThat(o.published()).isEqualTo(5);
        assertThat(o.failed()).isEqualTo(1);
        assertThat(o.rejected()).isZero();
        assertThat(o.byStatus()).hasSize(PostStatus.values().length);
    }
}
