package com.ai.xbot.controller;

import com.ai.xbot.dto.CurrentUserDto;
import com.ai.xbot.security.AuthenticatedUser;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Login/logout are handled by Spring Security filters; see SecurityConfig. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Primes the XSRF-TOKEN cookie before the SPA's first POST. */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName());
    }

    @GetMapping("/me")
    public CurrentUserDto me(@AuthenticationPrincipal AuthenticatedUser user) {
        return new CurrentUserDto(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }
}
