package com.ai.xbot.repository;

import com.ai.xbot.entity.NotificationLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {
    List<NotificationLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
    long countByReadAtIsNull();
    List<NotificationLog> findByReadAtIsNull();
}
