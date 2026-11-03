package com.ai.xbot.repository;

import com.ai.xbot.entity.PublishedPost;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublishedPostRepository extends JpaRepository<PublishedPost, UUID> {
    boolean existsByPostId(UUID postId);
    Optional<PublishedPost> findByPostId(UUID postId);
    List<PublishedPost> findByPostIdIn(Collection<UUID> postIds);
    List<PublishedPost> findAllByOrderByPublishedAtDesc(Pageable pageable);
}
