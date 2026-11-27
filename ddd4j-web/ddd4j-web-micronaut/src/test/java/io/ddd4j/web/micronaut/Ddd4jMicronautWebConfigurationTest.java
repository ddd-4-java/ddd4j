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

/**
 * 通过 <code>cfg.getPublicPaths()</code>、<code>cfg.getDefaultAuthenticationMode()</code>、<code>cfg.isIdempotencyEnabled()</code> 验证 <code>Ddd4jMicronautWebConfiguration</code> 的运行行为。
 */
class Ddd4jMicronautWebConfigurationTest {

    /**
     * 验证<code>cfg.getDefaultAuthenticationMode()</code> 等于 <code>AuthenticationMode.REQUIRED</code>，<code>cfg.isIdempotencyEnabled()</code>为真，<code>cfg.isTrustForwardedHeaders()</code>为假。
     */
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

    /**
     * 验证<code>cfg.getPublicPaths()</code> 等于 <code>List.of("/api")</code>，<code>cfg.getPublicPaths()</code> 与…为不同的实例 <code>paths</code>，<code>cfg.isIdempotencyEnabled()</code>为假。
     */
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

    /**
     * 验证<code>cfg.setPublicPaths(null)</code> 抛出 <code>NullPointerException.class</code>，<code>cfg.setDefaultAuthenticationMode(null)</code> 抛出 <code>NullPointerException.class</code>，<code>cfg.setIdempotencyTtl(null)</code> 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void nullRejected() {
        Ddd4jMicronautWebConfiguration cfg = new Ddd4jMicronautWebConfiguration();
        assertThrows(NullPointerException.class, () -> cfg.setPublicPaths(null));
        assertThrows(NullPointerException.class, () -> cfg.setDefaultAuthenticationMode(null));
        assertThrows(NullPointerException.class, () -> cfg.setIdempotencyCacheName(null));
        assertThrows(NullPointerException.class, () -> cfg.setIdempotencyTtl(null));
    }
}
