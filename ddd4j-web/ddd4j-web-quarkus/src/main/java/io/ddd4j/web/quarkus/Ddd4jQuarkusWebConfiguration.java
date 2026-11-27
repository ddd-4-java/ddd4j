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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Quarkus Web 配置，并兼容早期 {@code ddd4j.quarkus.web.*} 访问控制属性。
 */
@Getter
@Setter
public class Ddd4jQuarkusWebConfiguration {

    /** Quarkus Web \u8bbf\u95ee\u76f8\u5173\u914d\u7f6e\u524d\u7f00\u3002 */
    private static final String PREFIX = "ddd4j.web.";
    /** \u517c\u5bb9\u65e7 Quarkus \u9274\u6743\u914d\u7f6e\u7684\u5c5e\u6027\u524d\u7f00\u3002 */
    private static final String LEGACY_PREFIX = "ddd4j.quarkus.web.";

    /** \u8df3\u8fc7\u9ed8\u8ba4\u8ba4\u8bc1\u62e6\u622a\u7684\u516c\u5f00\u8def\u5f84\u914d\u7f6e\u3002 */
    private List<String> publicPaths = new ArrayList<>(java.util.Arrays.asList("/health", "/q/health/**"));
    /** \u672a\u5355\u72ec\u914d\u7f6e\u8def\u5f84\u65f6\u91c7\u7528\u7684\u9ed8\u8ba4\u9274\u6743\u6a21\u5f0f\u3002 */
    private AuthenticationMode defaultAuthenticationMode = AuthenticationMode.REQUIRED;
    /** \u662f\u5426\u5df2\u7ecf\u914d\u7f6e\u65b0\u7684\u7edf\u4e00 Web \u8bbf\u95ee\u7b56\u7565\u3002 */
    private boolean standardAccessConfigured;
    /** \u517c\u5bb9\u914d\u7f6e\u662f\u5426\u8981\u6c42\u643a\u5e26 Bearer \u4ee4\u724c\u3002 */
    private boolean legacyBearerRequired;
    /** \u65e7\u7248\u914d\u7f6e\u4e2d\u542f\u7528\u8ba4\u8bc1\u7684\u8def\u5f84\u524d\u7f00\u3002 */
    private String legacyProtectedPathPrefix = "/";
    /** \u662f\u5426\u5141\u8bb8\u7528\u53cd\u5411\u4ee3\u7406\u8f6c\u53d1\u5934\u89e3\u6790\u5ba2\u6237\u7aef\u5730\u5740\u3002 */
    private boolean trustForwardedHeaders;
    /** \u662f\u5426\u7ed9\u5339\u914d\u8def\u7531\u542f\u7528\u5e42\u7b49\u9632\u62a4\u3002 */
    private boolean idempotencyEnabled = true;
    /** \u5e42\u7b49\u68c0\u67e5\u67e5\u627e\u7684\u672c\u5730\u7f13\u5b58\u6ce8\u518c\u540d\u3002 */
    private String idempotencyCacheName = "ddd4j-web-idempotency";
    /** \u5e42\u7b49\u6761\u76ee\u5728\u7f13\u5b58\u4e2d\u7684\u5b58\u6d3b\u65f6\u95f4\u914d\u7f6e\u3002 */
    private Duration idempotencyTtl = Duration.ofMinutes(5);

    /**
     * load() 方法。
     * @return 当前对象自身，支持链式调用。
     */
    public static Ddd4jQuarkusWebConfiguration load() {
        return from(ConfigProvider.getConfig());
    }

    /**
     * from() 方法。
     * @param config 二维码模块配置。
     * @return 当前对象自身，支持链式调用。
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
                /**
                 * .ifPresent() 方法。
                 * @param configuration::setIdempotencyEnabled configuration::setIdempotencyEnabled 参数。
                 */
                .ifPresent(configuration::setIdempotencyEnabled);
        source.getOptionalValue(PREFIX + "idempotency.cache-name", String.class)
                .ifPresent(configuration::setIdempotencyCacheName);
        source.getOptionalValue(PREFIX + "idempotency.ttl", String.class)
                .map(Duration::parse)
                .ifPresent(configuration::setIdempotencyTtl);
        return configuration;
    }

    /**
     * accessPolicy() 方法。
     * @return 方法执行结果。
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
