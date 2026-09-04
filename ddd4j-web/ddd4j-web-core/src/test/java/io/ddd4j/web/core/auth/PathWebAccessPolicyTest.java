package io.ddd4j.web.core.auth;

import java.util.Collections;
import io.ddd4j.web.core.context.WebRequestContext;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PathWebAccessPolicyTest {

    private static WebRequestContext request(String path) {
        return new WebRequestContext("r-1", "t-1", "tenant-a", null,
                Locale.CHINA, "127.0.0.1", "GET", path);
    }

    @Test
    void exactPatternMatchesOnlyExactPath() {
        PathWebAccessPolicy policy = new PathWebAccessPolicy(Collections.singletonList("/health"), AuthenticationMode.REQUIRED);

        assertEquals(AuthenticationMode.DISABLED, policy.authenticationMode(request("/health")));
        assertEquals(AuthenticationMode.REQUIRED, policy.authenticationMode(request("/health/details")));
        assertEquals(AuthenticationMode.REQUIRED, policy.authenticationMode(request("/api")));
    }

    @Test
    void prefixPatternMatchesPrefixAndSubPaths() {
        PathWebAccessPolicy policy = new PathWebAccessPolicy(Collections.singletonList("/health/**"), AuthenticationMode.REQUIRED);

        assertEquals(AuthenticationMode.DISABLED, policy.authenticationMode(request("/health")));
        assertEquals(AuthenticationMode.DISABLED, policy.authenticationMode(request("/health/readiness")));
        assertEquals(AuthenticationMode.DISABLED, policy.authenticationMode(request("/health/readiness/deep")));
        assertEquals(AuthenticationMode.REQUIRED, policy.authenticationMode(request("/healthz")));
    }

    @Test
    void constructorFiltersBlankTrimsAndDeduplicates() {
        PathWebAccessPolicy policy = new PathWebAccessPolicy(
                Arrays.asList("  /api  ", "", "   ", "/api", "/admin/**"), AuthenticationMode.OPTIONAL);

        assertEquals(AuthenticationMode.DISABLED, policy.authenticationMode(request("/api")));
        assertEquals(AuthenticationMode.DISABLED, policy.authenticationMode(request("/admin/users")));
        assertEquals(AuthenticationMode.OPTIONAL, policy.authenticationMode(request("/other")));
    }

    @Test
    void constructorRejectsNull() {
        assertThrows(NullPointerException.class, () -> new PathWebAccessPolicy(null, AuthenticationMode.REQUIRED));
        assertThrows(NullPointerException.class,
                () -> new PathWebAccessPolicy(Collections.singletonList("/health"), null));
    }

    @Test
    void emptyPatternsDisableNothing() {
        PathWebAccessPolicy policy = new PathWebAccessPolicy(Arrays.asList(), AuthenticationMode.DISABLED);
        assertEquals(AuthenticationMode.DISABLED, policy.authenticationMode(request("/anything")));
    }
}
