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
import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Micronaut Web 适配配置。
 */
@Getter
@ConfigurationProperties("ddd4j.web")
public class Ddd4jMicronautWebConfiguration {

    private List<String> publicPaths = new ArrayList<>(List.of(
            "/health", "/health/readiness", "/health/liveness"));
    private AuthenticationMode defaultAuthenticationMode = AuthenticationMode.REQUIRED;
    @Setter
    private boolean trustForwardedHeaders;
    @Setter
    private boolean idempotencyEnabled = true;
    private String idempotencyCacheName = "ddd4j-web-idempotency";
    private Duration idempotencyTtl = Duration.ofMinutes(5);

    // Micronaut 在 Lombok 之前分析配置元数据，因此 setter 必须显式存在于源码 AST 中。
    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = new ArrayList<>(Objects.requireNonNull(publicPaths, "publicPaths must not be null"));
    }

    public void setDefaultAuthenticationMode(AuthenticationMode defaultAuthenticationMode) {
        this.defaultAuthenticationMode = Objects.requireNonNull(defaultAuthenticationMode,
                "defaultAuthenticationMode must not be null");
    }

    public void setIdempotencyCacheName(String idempotencyCacheName) {
        this.idempotencyCacheName = Objects.requireNonNull(idempotencyCacheName,
                "idempotencyCacheName must not be null");
    }

    public void setIdempotencyTtl(Duration idempotencyTtl) {
        this.idempotencyTtl = Objects.requireNonNull(idempotencyTtl, "idempotencyTtl must not be null");
    }
}
