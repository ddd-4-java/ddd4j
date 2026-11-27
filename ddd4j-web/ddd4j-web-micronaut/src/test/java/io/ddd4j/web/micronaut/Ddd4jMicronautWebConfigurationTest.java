/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.web.micronaut;

import io.ddd4j.web.core.auth.AuthenticationMode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** \u901a\u8fc7 <code>cfg.getPublicPaths()</code>\u3001<code>cfg.getDefaultAuthenticationMode()</code>\u3001<code>cfg.isIdempotencyEnabled()</code> \u9a8c\u8bc1 <code>Ddd4jMicronautWebConfiguration</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class Ddd4jMicronautWebConfigurationTest {

    /** \u9a8c\u8bc1<code>cfg.getDefaultAuthenticationMode()</code> \u7b49\u4e8e <code>AuthenticationMode.REQUIRED</code>\uff0c<code>cfg.isIdempotencyEnabled()</code>\u4e3a\u771f\uff0c<code>cfg.isTrustForwardedHeaders()</code>\u4e3a\u5047\u3002 */
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

    /** \u9a8c\u8bc1<code>cfg.getPublicPaths()</code> \u7b49\u4e8e <code>List.of("/api")</code>\uff0c<code>cfg.getPublicPaths()</code> \u4e0e\u2026\u4e3a\u4e0d\u540c\u7684\u5b9e\u4f8b <code>paths</code>\uff0c<code>cfg.isIdempotencyEnabled()</code>\u4e3a\u5047\u3002 */
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

    /** \u9a8c\u8bc1<code>cfg.setPublicPaths(null)</code> \u629b\u51fa <code>NullPointerException.class</code>\uff0c<code>cfg.setDefaultAuthenticationMode(null)</code> \u629b\u51fa <code>NullPointerException.class</code>\uff0c<code>cfg.setIdempotencyTtl(null)</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void nullRejected() {
        Ddd4jMicronautWebConfiguration cfg = new Ddd4jMicronautWebConfiguration();
        assertThrows(NullPointerException.class, () -> cfg.setPublicPaths(null));
        assertThrows(NullPointerException.class, () -> cfg.setDefaultAuthenticationMode(null));
        assertThrows(NullPointerException.class, () -> cfg.setIdempotencyCacheName(null));
        assertThrows(NullPointerException.class, () -> cfg.setIdempotencyTtl(null));
    }
}
