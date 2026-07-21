package io.ddd4j.auth.security.subject;

import io.ddd4j.core.auth.AuthPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.Objects;

/**
 * Spring Security UserDetails carrying the framework-neutral ddd4j principal.
 */
public class AuthUserDetails extends User {

    private final AuthPrincipal authPrincipal;

    public AuthUserDetails(String username,
                           String password,
                           boolean enabled,
                           Collection<? extends GrantedAuthority> authorities,
                           AuthPrincipal authPrincipal) {
        super(username, password, enabled, true, true, true, authorities);
        this.authPrincipal = Objects.requireNonNull(authPrincipal, "authPrincipal must not be null");
    }

    public AuthPrincipal getAuthPrincipal() {
        return authPrincipal;
    }
}
