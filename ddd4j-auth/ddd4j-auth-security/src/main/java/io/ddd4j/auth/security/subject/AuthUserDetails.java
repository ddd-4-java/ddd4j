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

    /** \u6846\u67b6\u65e0\u5173\u7684\u8ba4\u8bc1\u4e3b\u4f53\u3002 */
    private final AuthPrincipal authPrincipal;

    /**
     * \u4f7f\u7528\u8ba4\u8bc1\u4e3b\u4f53\u53ca Spring Security \u8d26\u53f7\u5c5e\u6027\u521b\u5efa\u7528\u6237\u8be6\u60c5\u3002
     * @param username \u7528\u6237\u540d
     * @param password \u5bc6\u7801
     * @param enabled \u8d26\u53f7\u662f\u5426\u542f\u7528
     * @param authorities \u5df2\u6388\u4e88\u6743\u9650
     * @param authPrincipal ddd4j \u8ba4\u8bc1\u4e3b\u4f53
     */
    public AuthUserDetails(String username,
                           String password,
                           boolean enabled,
                           Collection<? extends GrantedAuthority> authorities,
                           AuthPrincipal authPrincipal) {
        super(username, password, enabled, true, true, true, authorities);
        this.authPrincipal = Objects.requireNonNull(authPrincipal, "authPrincipal must not be null");
    }

    /**
     * \u83b7\u53d6\u6846\u67b6\u65e0\u5173\u7684\u8ba4\u8bc1\u4e3b\u4f53\u3002
     * @return ddd4j \u8ba4\u8bc1\u4e3b\u4f53
     */
    public AuthPrincipal getAuthPrincipal() {
        return authPrincipal;
    }
}
