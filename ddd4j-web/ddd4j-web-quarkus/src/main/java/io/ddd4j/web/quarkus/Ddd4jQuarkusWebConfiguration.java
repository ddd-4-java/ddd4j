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
package io.ddd4j.web.quarkus;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.web.core.auth.AuthenticationMode;
import io.ddd4j.web.core.auth.PathWebAccessPolicy;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import lombok.Getter;
import lombok.Setter;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Quarkus Web 配置，并兼容早期 {@code ddd4j.quarkus.web.*} 访问控制属性。
 */
@Getter
@Setter
public class Ddd4jQuarkusWebConfiguration {

    private static final String PREFIX = "ddd4j.web.";
    private static final String LEGACY_PREFIX = "ddd4j.quarkus.web.";

    private List<String> publicPaths = new ArrayList<>(List.of("/health", "/q/health/**"));
    private AuthenticationMode defaultAuthenticationMode = AuthenticationMode.REQUIRED;
    private boolean standardAccessConfigured;
    private boolean legacyBearerRequired;
    private String legacyProtectedPathPrefix = "/";
    private boolean trustForwardedHeaders;
    private boolean idempotencyEnabled = true;
    private String idempotencyCacheName = "ddd4j-web-idempotency";
    private Duration idempotencyTtl = Duration.ofMinutes(5);

    public static Ddd4jQuarkusWebConfiguration load() {
        return from(ConfigProvider.getConfig());
    }

    public static Ddd4jQuarkusWebConfiguration from(Config config) {
        Config source = Objects.requireNonNull(config, "config must not be null");
        Ddd4jQuarkusWebConfiguration configuration = new Ddd4jQuarkusWebConfiguration();
        source.getOptionalValues(PREFIX + "public-paths", String.class).ifPresent(values -> {
            configuration.setPublicPaths(new ArrayList<>(values));
            configuration.setStandardAccessConfigured(true);
        });
        source.getOptionalValue(PREFIX + "authentication-mode", String.class).ifPresent(value -> {
            configuration.setDefaultAuthenticationMode(
                    AuthenticationMode.valueOf(value.toUpperCase(Locale.ROOT)));
            configuration.setStandardAccessConfigured(true);
        });
        source.getOptionalValue(LEGACY_PREFIX + "bearer-required", Boolean.class)
                .ifPresent(configuration::setLegacyBearerRequired);
        source.getOptionalValue(LEGACY_PREFIX + "protected-path-prefix", String.class)
                .ifPresent(configuration::setLegacyProtectedPathPrefix);
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

    public WebAccessPolicy accessPolicy() {
        if (standardAccessConfigured) {
            return new PathWebAccessPolicy(publicPaths, defaultAuthenticationMode);
        }
        if (!legacyBearerRequired) {
            return WebAccessPolicy.disabled();
        }
        String protectedPrefix = StrKit.isBlank(legacyProtectedPathPrefix) ? "/" : legacyProtectedPathPrefix;
        PathWebAccessPolicy healthPolicy = new PathWebAccessPolicy(publicPaths, AuthenticationMode.REQUIRED);
        return context -> context.path().startsWith(protectedPrefix)
                ? healthPolicy.authenticationMode(context) : AuthenticationMode.DISABLED;
    }
}
