package com.ai.xbot.repository;

import com.ai.xbot.entity.PostApproval;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PostApprovalRepository extends JpaRepository<PostApproval, UUID> {

    @Query("select a from PostApproval a where a.postId = :postId and a.decision = 'APPROVED' and a.invalidatedAt is null")
    Optional<PostApproval> findLiveApproval(UUID postId);

    List<PostApproval> findByPostIdOrderByDecidedAtDesc(UUID postId);
}
