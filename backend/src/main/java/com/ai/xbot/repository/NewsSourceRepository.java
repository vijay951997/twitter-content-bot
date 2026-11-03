package com.ai.xbot.repository;

import com.ai.xbot.entity.NewsSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsSourceRepository extends JpaRepository<NewsSource, UUID> {
    List<NewsSource> findAllByOrderByNameAsc();
    List<NewsSource> findByEnabledTrue();
    Optional<NewsSource> findByBaseUrl(String baseUrl);
}
