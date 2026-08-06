package io.ddd4j.web.dropwizard;

import io.ddd4j.web.core.auth.AuthenticationMode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ddd4jDropwizardWebConfigurationTest {

    @Test
    void defaults() {
        Ddd4jDropwizardWebConfiguration cfg = new Ddd4jDropwizardWebConfiguration();
        assertEquals(List.of("/health", "/healthcheck/**"), cfg.getPublicPaths());
        assertEquals(AuthenticationMode.REQUIRED, cfg.getDefaultAuthenticationMode());
        assertTrue(cfg.isIdempotencyEnabled());
        assertEquals("ddd4j-web-idempotency", cfg.getIdempotencyCacheName());
        assertEquals(Duration.ofMinutes(5), cfg.getIdempotencyTtl());
        assertFalse(cfg.isTrustForwardedHeaders());
    }

    @Test
    void setters() {
        Ddd4jDropwizardWebConfiguration cfg = new Ddd4jDropwizardWebConfiguration();
        cfg.setPublicPaths(List.of("/api"));
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
}
