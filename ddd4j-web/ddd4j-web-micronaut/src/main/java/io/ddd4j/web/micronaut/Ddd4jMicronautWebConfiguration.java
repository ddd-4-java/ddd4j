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

    /** \u8df3\u8fc7\u9ed8\u8ba4\u8ba4\u8bc1\u62e6\u622a\u7684\u516c\u5f00\u8def\u5f84\u914d\u7f6e\u3002 */
    private List<String> publicPaths = new ArrayList<>(List.of(
            "/health", "/health/readiness", "/health/liveness"));
    /** \u672a\u5355\u72ec\u914d\u7f6e\u8def\u5f84\u65f6\u91c7\u7528\u7684\u9ed8\u8ba4\u9274\u6743\u6a21\u5f0f\u3002 */
    private AuthenticationMode defaultAuthenticationMode = AuthenticationMode.REQUIRED;
    /** \u662f\u5426\u5141\u8bb8\u7528\u53cd\u5411\u4ee3\u7406\u8f6c\u53d1\u5934\u89e3\u6790\u5ba2\u6237\u7aef\u5730\u5740\u3002 */
    @Setter
    private boolean trustForwardedHeaders;
    /** \u662f\u5426\u7ed9\u5339\u914d\u8def\u7531\u542f\u7528\u5e42\u7b49\u9632\u62a4\u3002 */
    @Setter
    private boolean idempotencyEnabled = true;
    /** \u5e42\u7b49\u68c0\u67e5\u67e5\u627e\u7684\u672c\u5730\u7f13\u5b58\u6ce8\u518c\u540d\u3002 */
    private String idempotencyCacheName = "ddd4j-web-idempotency";
    /** \u5e42\u7b49\u6761\u76ee\u5728\u7f13\u5b58\u4e2d\u7684\u5b58\u6d3b\u65f6\u95f4\u914d\u7f6e\u3002 */
    private Duration idempotencyTtl = Duration.ofMinutes(5);

    // Micronaut 在 Lombok 之前分析配置元数据，因此 setter 必须显式存在于源码 AST 中。

    /**
     * 设置允许匿名访问的路径。
     *
     * @param publicPaths 公开路径列表
     */
    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = new ArrayList<>(Objects.requireNonNull(publicPaths, "publicPaths must not be null"));
    }

    /**
     * 设置默认认证模式。
     *
     * @param defaultAuthenticationMode 默认认证模式
     */
    public void setDefaultAuthenticationMode(AuthenticationMode defaultAuthenticationMode) {
        this.defaultAuthenticationMode = Objects.requireNonNull(defaultAuthenticationMode,
                "defaultAuthenticationMode must not be null");
    }

    /**
     * 设置幂等缓存名称。
     *
     * @param idempotencyCacheName 缓存名称
     */
    public void setIdempotencyCacheName(String idempotencyCacheName) {
        this.idempotencyCacheName = Objects.requireNonNull(idempotencyCacheName,
                "idempotencyCacheName must not be null");
    }

    /**
     * 设置幂等记录有效期。
     *
     * @param idempotencyTtl 有效期
     */
    public void setIdempotencyTtl(Duration idempotencyTtl) {
        this.idempotencyTtl = Objects.requireNonNull(idempotencyTtl, "idempotencyTtl must not be null");
    }
}
