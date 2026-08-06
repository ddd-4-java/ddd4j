package io.ddd4j.web.micronaut;

import io.ddd4j.web.core.auth.AuthenticationMode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ddd4jMicronautWebConfigurationTest {

    @Test
    void defaults() {
        Ddd4jMicronautWebConfiguration cfg = new Ddd4jMicronautWebConfiguration();
        assertEquals(List.of("/health", "/health/readiness", "/health/liveness"), cfg.getPublicPaths());
        assertEquals(AuthenticationMode.REQUIRED, cfg.getDefaultAuthenticationMode());
        assertTrue(cfg.isIdempotencyEnabled());
        assertEquals("ddd4j-web-idempotency", cfg.getIdempotencyCacheName());
        assertEquals(Duration.ofMinutes(5), cfg.getIdempotencyTtl());
        assertFalse(cfg.isTrustForwardedHeaders());
    }

    @Test
    void setters() {
        Ddd4jMicronautWebConfiguration cfg = new Ddd4jMicronautWebConfiguration();

        List<String> paths = new ArrayList<>(List.of("/api"));
        cfg.setPublicPaths(paths);
        assertEquals(List.of("/api"), cfg.getPublicPaths());
        assertNotSame(paths, cfg.getPublicPaths());
        paths.add("/mutated");
        assertEquals(List.of("/api"), cfg.getPublicPaths());

        cfg.setDefaultAuthenticationMode(AuthenticationMode.OPTIONAL);
        assertEquals(AuthenticationMode.OPTIONAL, cfg.getDefaultAuthenticationMode());

        cfg.setIdempotencyCacheName("cache");
        assertEquals("cache", cfg.getIdempotencyCacheName());

        cfg.setIdempotencyTtl(Duration.ofSeconds(30));
        assertEquals(Duration.ofSeconds(30), cfg.getIdempotencyTtl());

        cfg.setTrustForwardedHeaders(true);
        assertTrue(cfg.isTrustForwardedHeaders());

        cfg.setIdempotencyEnabled(false);
        assertFalse(cfg.isIdempotencyEnabled());
    }

    @Test
    void nullRejected() {
        Ddd4jMicronautWebConfiguration cfg = new Ddd4jMicronautWebConfiguration();
        assertThrows(NullPointerException.class, () -> cfg.setPublicPaths(null));
        assertThrows(NullPointerException.class, () -> cfg.setDefaultAuthenticationMode(null));
        assertThrows(NullPointerException.class, () -> cfg.setIdempotencyCacheName(null));
        assertThrows(NullPointerException.class, () -> cfg.setIdempotencyTtl(null));
    }
}
