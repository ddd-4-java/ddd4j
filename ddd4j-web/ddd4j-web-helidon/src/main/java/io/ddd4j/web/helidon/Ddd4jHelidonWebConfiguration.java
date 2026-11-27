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
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Helidon MP Web 配置，统一使用 {@code ddd4j.web.*} 配置命名空间。
 */
@Getter
@Setter
public class Ddd4jHelidonWebConfiguration {

    /** Helidon Web \u8bbf\u95ee\u76f8\u5173\u914d\u7f6e\u524d\u7f00\u3002 */
    private static final String PREFIX = "ddd4j.web.";

    /** \u8df3\u8fc7\u9ed8\u8ba4\u8ba4\u8bc1\u62e6\u622a\u7684\u516c\u5f00\u8def\u5f84\u914d\u7f6e\u3002 */
    private List<String> publicPaths = new ArrayList<>(List.of(
            "/health", "/health/**", "/metrics", "/openapi/**"));
    /** \u672a\u5355\u72ec\u914d\u7f6e\u8def\u5f84\u65f6\u91c7\u7528\u7684\u9ed8\u8ba4\u9274\u6743\u6a21\u5f0f\u3002 */
    private AuthenticationMode defaultAuthenticationMode = AuthenticationMode.REQUIRED;
    /** \u662f\u5426\u5141\u8bb8\u7528\u53cd\u5411\u4ee3\u7406\u8f6c\u53d1\u5934\u89e3\u6790\u5ba2\u6237\u7aef\u5730\u5740\u3002 */
    private boolean trustForwardedHeaders;
    /** \u662f\u5426\u7ed9\u5339\u914d\u8def\u7531\u542f\u7528\u5e42\u7b49\u9632\u62a4\u3002 */
    private boolean idempotencyEnabled = true;
    /** \u5e42\u7b49\u68c0\u67e5\u67e5\u627e\u7684\u672c\u5730\u7f13\u5b58\u6ce8\u518c\u540d\u3002 */
    private String idempotencyCacheName = "ddd4j-web-idempotency";
    /** \u5e42\u7b49\u6761\u76ee\u5728\u7f13\u5b58\u4e2d\u7684\u5b58\u6d3b\u65f6\u95f4\u914d\u7f6e\u3002 */
    private Duration idempotencyTtl = Duration.ofMinutes(5);

    /**
     * 从当前 MicroProfile 配置加载 Web 属性。
     *
     * @return Web 配置
     */
    public static Ddd4jHelidonWebConfiguration load() {
        return from(ConfigProvider.getConfig());
    }

    /**
     * 从指定配置源加载 Web 属性。
     *
     * @param config MicroProfile 配置源
     * @return Web 配置
     */
    public static Ddd4jHelidonWebConfiguration from(Config config) {
        Config source = Objects.requireNonNull(config, "config must not be null");
        Ddd4jHelidonWebConfiguration configuration = new Ddd4jHelidonWebConfiguration();
        source.getOptionalValues(PREFIX + "public-paths", String.class)
                .ifPresent(values -> configuration.setPublicPaths(trimPublicPaths(values)));
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

    /**
     * 去除配置列表中每个公开路径两侧的空白，保留路径顺序和可修改列表语义。
     *
     * @param paths 由当前 MicroProfile Config 版本转换的路径列表
     * @return 去除首尾空白的路径列表
     */
    private static List<String> trimPublicPaths(List<String> paths) {
        List<String> result = new ArrayList<>(paths.size());
        for (String path : paths) {
            result.add(path.trim());
        }
        return result;
    }
}
