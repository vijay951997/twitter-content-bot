package com.ai.xbot.security;

import com.ai.xbot.entity.User;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Security principal carrying the internal user id, so approvals and audits record who acted. */
public class AuthenticatedUser implements UserDetails {

    private final UUID id;
    private final String username;
    private final String passwordHash;
    private final String displayName;
    private final String role;
    private final boolean enabled;

    public AuthenticatedUser(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.passwordHash = user.getPasswordHash();
        this.displayName = user.getDisplayName();
        this.role = user.getRole().name();
        this.enabled = user.isEnabled();
    }

    public UUID getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getRole() { return role; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    @Override public boolean isEnabled() { return enabled; }
}
