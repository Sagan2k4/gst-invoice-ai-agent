package com.gstagent.auth;

import com.gstagent.user.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

/** The authenticated principal, rebuilt from JWT claims on every request (no DB hit). */
public record AuthUser(Long userId, Long businessId, String email, Role role) {

    public Collection<? extends GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
