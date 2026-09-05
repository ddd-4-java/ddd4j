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
package io.ddd4j.web.helidon;

import io.ddd4j.web.core.auth.AuthenticationMode;
import lombok.Getter;
import lombok.Setter;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Helidon MP Web 配置，统一使用 {@code ddd4j.web.*} 配置命名空间。
 */
@Getter
@Setter
public class Ddd4jHelidonWebConfiguration {

    private static final String PREFIX = "ddd4j.web.";

    private List<String> publicPaths = new ArrayList<>(Arrays.asList(
            "/health", "/health/**", "/metrics", "/openapi/**"));
    private AuthenticationMode defaultAuthenticationMode = AuthenticationMode.REQUIRED;
    private boolean trustForwardedHeaders;
    private boolean idempotencyEnabled = true;
    private String idempotencyCacheName = "ddd4j-web-idempotency";
    private Duration idempotencyTtl = Duration.ofMinutes(5);

    public static Ddd4jHelidonWebConfiguration load() {
        return from(ConfigProvider.getConfig());
    }

    public static Ddd4jHelidonWebConfiguration from(Config config) {
        Config source = Objects.requireNonNull(config, "config must not be null");
        Ddd4jHelidonWebConfiguration configuration = new Ddd4jHelidonWebConfiguration();
        source.getOptionalValues(PREFIX + "public-paths", String.class)
                .ifPresent(values -> configuration.setPublicPaths(new ArrayList<>(values)));
        source.getOptionalValue(PREFIX + "authentication-mode", String.class)
                .map(value -> AuthenticationMode.valueOf(value.toUpperCase(Locale.ROOT)))
                .ifPresent(configuration::setDefaultAuthenticationMode);
        source.getOptionalValue(PREFIX + "trust-forwarded-headers", Boolean.class)
                .ifPresent(configuration::setTrustForwardedHeaders);
        source.getOptionalValue(PREFIX + "idempotency.enabled", Boolean.class)
                .ifPresent(configuration::setIdempotencyEnabled);
        source.getOptionalValue(PREFIX + "idempotency.cache-name", String.class)
                .ifPresent(configuration::setIdempotencyCacheName);
        source.getOptionalValue(PREFIX + "idempotency.ttl", String.class)
                .map(Duration::parse)
                .ifPresent(configuration::setIdempotencyTtl);
        return configuration;
    }
}
