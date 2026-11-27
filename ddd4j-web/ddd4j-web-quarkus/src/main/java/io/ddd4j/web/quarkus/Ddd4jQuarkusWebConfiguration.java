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
import java.util.*;
import java.util.stream.Collectors;

/**
 * Quarkus Web 配置，并兼容早期 {@code ddd4j.quarkus.web.*} 访问控制属性。
 */
@Getter
@Setter
public class Ddd4jQuarkusWebConfiguration {

    /**
     * Quarkus Web 访问相关配置前缀。
     */
    private static final String PREFIX = "ddd4j.web.";
    /**
     * 兼容旧 Quarkus 鉴权配置的属性前缀。
     */
    private static final String LEGACY_PREFIX = "ddd4j.quarkus.web.";

    /**
     * 跳过默认认证拦截的公开路径配置。
     */
    private List<String> publicPaths = new ArrayList<>(java.util.Arrays.asList("/health", "/q/health/**"));
    /**
     * 未单独配置路径时采用的默认鉴权模式。
     */
    private AuthenticationMode defaultAuthenticationMode = AuthenticationMode.REQUIRED;
    /**
     * 是否已经配置新的统一 Web 访问策略。
     */
    private boolean standardAccessConfigured;
    /**
     * 兼容配置是否要求携带 Bearer 令牌。
     */
    private boolean legacyBearerRequired;
    /**
     * 旧版配置中启用认证的路径前缀。
     */
    private String legacyProtectedPathPrefix = "/";
    /**
     * 是否允许用反向代理转发头解析客户端地址。
     */
    private boolean trustForwardedHeaders;
    /**
     * 是否给匹配路由启用幂等防护。
     */
    private boolean idempotencyEnabled = true;
    /**
     * 幂等检查查找的本地缓存注册名。
     */
    private String idempotencyCacheName = "ddd4j-web-idempotency";
    /**
     * 幂等条目在缓存中的存活时间配置。
     */
    private Duration idempotencyTtl = Duration.ofMinutes(5);

    /**
     * 从当前 MicroProfile 配置加载 Web 配置。
     *
     * @return Web 配置
     */
    public static Ddd4jQuarkusWebConfiguration load() {
        return from(ConfigProvider.getConfig());
    }

    /**
     * 从指定配置源读取标准属性与兼容属性。
     *
     * @param config MicroProfile 配置源
     * @return Web 配置
     */
    public static Ddd4jQuarkusWebConfiguration from(Config config) {
        Config source = Objects.requireNonNull(config, "config must not be null");
        Ddd4jQuarkusWebConfiguration configuration = new Ddd4jQuarkusWebConfiguration();
        source.getOptionalValue(PREFIX + "public-paths", String.class).ifPresent(value -> {
            configuration.setPublicPaths(Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(path -> !StrKit.isBlank(path))
                    .collect(Collectors.toList()));
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

    /**
     * 根据标准属性或旧版 Bearer 属性创建访问策略。
     *
     * @return 当前配置对应的访问策略
     */
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
