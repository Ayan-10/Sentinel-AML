package com.meridiantrust.sentinel.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Resolves the acting identity for audit records.
 *
 * <p>NFR (Auditability): every state transition is attributed to a real actor.
 * Reading the SecurityContext through one injected component — rather than
 * calling the static holder from a dozen services — keeps services testable
 * without a populated security context.
 */
@Component
public class CurrentUser {

    private static final String SYSTEM = "SYSTEM";

    public String username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            // Detection runs without a human actor; attribute it to the engine.
            return SYSTEM;
        }
        return auth.getName();
    }

    public String roles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return SYSTEM;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
    }

    public boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        String target = "ROLE_" + role;
        return auth.getAuthorities().stream().anyMatch(a -> target.equals(a.getAuthority()));
    }
}
