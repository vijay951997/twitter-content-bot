package com.ai.xbot.service.notify;

import com.ai.xbot.entity.NotificationLog;
import com.ai.xbot.repository.NotificationLogRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Dashboard notifications. Written in their own transaction so a failure elsewhere never loses them. */
@Service
public class NotificationService {

    private final NotificationLogRepository repository;

    public NotificationService(NotificationLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notify(String type, String title, String message, String entityType, UUID entityId) {
        repository.save(new NotificationLog(type, title, message, entityType, entityId));
    }
}
