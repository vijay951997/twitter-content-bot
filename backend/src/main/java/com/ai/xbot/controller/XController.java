package com.ai.xbot.controller;

import com.ai.xbot.dto.XAccountStatusDto;
import com.ai.xbot.security.AuthenticatedUser;
import com.ai.xbot.service.xaccount.XAccountService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Connect / disconnect the X account. The browser is sent to x.com to sign in; no password passes through here. */
@RestController
@RequestMapping("/api/x")
public class XController {

    private final XAccountService accounts;

    public XController(XAccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/status")
    public XAccountStatusDto status() {
        return accounts.status();
    }

    /** Top-level navigation from the dashboard: redirects the browser to X's consent page. */
    @GetMapping("/connect")
    public ResponseEntity<Void> connect(HttpServletRequest req, @AuthenticationPrincipal AuthenticatedUser user) {
        String url = accounts.startConnect(req.getSession(true), user.getId());
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    /** X redirects here after consent. Always returns the user to Settings with an outcome flag. */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String state,
                                         @RequestParam(required = false) String code,
                                         @RequestParam(required = false) String error,
                                         HttpServletRequest req, @AuthenticationPrincipal AuthenticatedUser user) {
        String outcome = accounts.completeConnect(req.getSession(true), user.getId(), state, code, error);
        String target = "/settings?x=" + (outcome == null ? "connected" : "error&reason=" + outcome);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
    }

    @PostMapping("/disconnect")
    public XAccountStatusDto disconnect(@AuthenticationPrincipal AuthenticatedUser user) {
        accounts.disconnect(user.getId());
        return accounts.status();
    }
}
