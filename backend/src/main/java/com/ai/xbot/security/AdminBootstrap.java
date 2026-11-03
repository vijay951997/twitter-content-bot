package com.ai.xbot.security;

import com.ai.xbot.audit.AuditService;
import com.ai.xbot.config.AppProperties;
import com.ai.xbot.entity.User;
import com.ai.xbot.entity.enums.ActorType;
import com.ai.xbot.entity.enums.UserRole;
import com.ai.xbot.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first ADMIN user from APP_ADMIN_USERNAME / APP_ADMIN_PASSWORD when the users table is empty.
 * There is no default password: if none is configured, no user is created and login is impossible.
 * The password itself is never logged.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    static final int MIN_PASSWORD_LENGTH = 12;
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;
    private final AuditService auditService;

    public AdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          AppProperties properties, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) return;

        AppProperties.Admin admin = properties.admin();
        String username = admin == null ? null : admin.username();
        String password = admin == null ? null : admin.password();

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("No users exist and APP_ADMIN_USERNAME/APP_ADMIN_PASSWORD are not set. Dashboard login is disabled.");
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("APP_ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        User user = userRepository.save(new User(username, passwordEncoder.encode(password), username, UserRole.ADMIN));
        auditService.record(null, ActorType.SYSTEM, "ADMIN_BOOTSTRAPPED", "users", user.getId(), null, null,
                "Initial admin created from environment");
        log.info("Initial admin user '{}' created", username);
    }
}
