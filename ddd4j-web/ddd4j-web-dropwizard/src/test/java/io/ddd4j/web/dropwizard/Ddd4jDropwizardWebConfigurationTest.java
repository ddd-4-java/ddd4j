package io.ddd4j.web.dropwizard;

import io.ddd4j.web.core.auth.AuthenticationMode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** \u6821\u9a8c Dropwizard Web \u914d\u7f6e\u7684\u9ed8\u8ba4\u503c\u548c\u53ef\u914d\u7f6e\u9879\u3002 */
class Ddd4jDropwizardWebConfigurationTest {

    /** \u9a8c\u8bc1<code>cfg.getDefaultAuthenticationMode()</code> \u7b49\u4e8e <code>AuthenticationMode.REQUIRED</code>\uff0c<code>cfg.isIdempotencyEnabled()</code>\u4e3a\u771f\uff0c<code>cfg.isTrustForwardedHeaders()</code>\u4e3a\u5047\u3002 */
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

    /** \u9a8c\u8bc1<code>cfg.getPublicPaths()</code> \u7b49\u4e8e <code>Collections.singletonList("/api")</code>\uff0c<code>cfg.getDefaultAuthenticationMode()</code> \u7b49\u4e8e <code>AuthenticationMode.OPTIONAL</code>\uff0c<code>cfg.isIdempotencyEnabled()</code>\u4e3a\u5047\u3002 */
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
