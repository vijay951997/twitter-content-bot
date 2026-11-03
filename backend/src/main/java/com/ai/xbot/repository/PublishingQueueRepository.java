package com.ai.xbot.repository;

import com.ai.xbot.entity.PublishingQueueEntry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PublishingQueueRepository extends JpaRepository<PublishingQueueEntry, UUID> {

    @Query("select q from PublishingQueueEntry q where q.postId = :postId and q.status in ('PENDING','LOCKED')")
    Optional<PublishingQueueEntry> findActiveForPost(UUID postId);

    List<PublishingQueueEntry> findByPostIdOrderByCreatedAtDesc(UUID postId);

    /** Locks due rows; rows locked by another worker are skipped, so two workers never take the same post. */
    @Query(value = """
            select * from publishing_queue
            where status = 'PENDING' and scheduled_at <= :now and (next_attempt_at is null or next_attempt_at <= :now)
            order by scheduled_at
            limit :limit
            for update skip locked""", nativeQuery = true)
    List<PublishingQueueEntry> lockDue(Instant now, int limit);

    /** Entries a crashed worker left LOCKED. */
    @Query("select q from PublishingQueueEntry q where q.status = 'LOCKED' and q.lockedUntil < :now")
    List<PublishingQueueEntry> findStaleLocks(Instant now);

    @Query("select q from PublishingQueueEntry q where q.status = 'PENDING' order by q.scheduledAt")
    List<PublishingQueueEntry> findPending();
}
