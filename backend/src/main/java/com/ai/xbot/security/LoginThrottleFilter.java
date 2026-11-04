package com.ai.xbot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/** Refuses login attempts while the IP address or username is throttled. */
public class LoginThrottleFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoginThrottleFilter.class);
    private final LoginThrottle throttle;

    public LoginThrottleFilter(LoginThrottle throttle) {
        this.throttle = throttle;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && "/api/auth/login".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String ip = req.getRemoteAddr();
        String username = req.getParameter("username");
        if (throttle.isBlocked(ip, username)) {
            log.warn("Login throttled for ip={} username='{}'", ip, username);
            res.setStatus(429);
            res.setHeader("Retry-After", String.valueOf(throttle.retryAfterSeconds()));
            res.setContentType("application/json");
            res.getWriter().write("{\"status\":429,\"message\":\"Too many failed logins. Try again in 15 minutes.\"}");
            return;
        }
        chain.doFilter(req, res);
    }
}
