package com.ai.xbot.repository;

import com.ai.xbot.entity.FactualClaim;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FactualClaimRepository extends JpaRepository<FactualClaim, UUID> {
    List<FactualClaim> findByArticleIdOrderByCreatedAtAscIdAsc(UUID articleId);

    default List<FactualClaim> findByArticleIdOrderByCreatedAtAsc(UUID articleId) {
        return findByArticleIdOrderByCreatedAtAscIdAsc(articleId);
    }
    List<FactualClaim> findByArticleIdIn(Collection<UUID> articleIds);
    void deleteByArticleId(UUID articleId);
}
