package com.ai.xbot.repository;

import com.ai.xbot.entity.GeneratedPost;
import com.ai.xbot.entity.enums.PostStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GeneratedPostRepository extends JpaRepository<GeneratedPost, UUID> {

    /** Rows of [status, count] for every status that has at least one post. */
    @Query("select p.status, count(p) from GeneratedPost p group by p.status")
    List<Object[]> countGroupedByStatus();

    List<GeneratedPost> findByStatusAndProposedPublishAtAfterOrderByProposedPublishAtAsc(
            PostStatus status, Instant after, Pageable pageable);

    /** Recent drafts used for duplicate detection (rejected and cancelled drafts are ignored). */
    @Query("select p from GeneratedPost p where p.createdAt >= :since and p.status not in :ignored")
    List<GeneratedPost> findRecentForDuplicateCheck(Instant since, java.util.Collection<PostStatus> ignored);

    Page<GeneratedPost> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<GeneratedPost> findByStatusOrderByCreatedAtDesc(PostStatus status, Pageable pageable);
}
