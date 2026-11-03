package com.ai.xbot.repository;

import com.ai.xbot.entity.PostSource;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostSourceRepository extends JpaRepository<PostSource, UUID> {
    List<PostSource> findByPostIdIn(Collection<UUID> postIds);
}
