package com.ai.xbot.security;

import com.ai.xbot.audit.AuditService;
import com.ai.xbot.entity.enums.ActorType;
import com.ai.xbot.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LoginAuditListener {

    private static final Logger log = LoggerFactory.getLogger(LoginAuditListener.class);

    private final UserRepository userRepository;
    private final AuditService auditService;
    private final Clock clock;

    public LoginAuditListener(UserRepository userRepository, AuditService auditService, Clock clock) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @EventListener
    @Transactional
    public void onSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof AuthenticatedUser principal) {
            userRepository.findById(principal.getId()).ifPresent(u -> u.setLastLoginAt(Instant.now(clock)));
            auditService.record(principal.getId(), ActorType.USER, "LOGIN_SUCCESS", "users", principal.getId(),
                    null, null, null);
        }
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        // Username only; the submitted password is never logged.
        log.warn("Login failed for username '{}'", event.getAuthentication().getName());
    }
}
