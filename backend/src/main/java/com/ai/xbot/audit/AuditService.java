package com.ai.xbot.audit;

import com.ai.xbot.entity.AuditLog;
import com.ai.xbot.entity.enums.ActorType;
import com.ai.xbot.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes append-only audit entries. Runs inside the caller's transaction so that an
 * audited change and its audit row commit or roll back together.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AuditLog record(UUID actorId, ActorType actorType, String action, String entityType,
                           UUID entityId, Object oldValue, Object newValue, String details) {
        AuditLog entry = new AuditLog(actorId, actorType, action, entityType, entityId,
                toJson(oldValue), toJson(newValue), details);
        AuditLog saved = repository.save(entry);
        log.info("audit action={} entityType={} entityId={} actorType={}", action, entityType, entityId, actorType);
        return saved;
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Audit value is not serialisable", e);
        }
    }
}
