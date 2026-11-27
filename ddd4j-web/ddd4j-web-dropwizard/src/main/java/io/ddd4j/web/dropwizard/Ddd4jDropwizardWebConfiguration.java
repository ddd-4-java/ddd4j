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
package io.ddd4j.web.dropwizard;

import io.ddd4j.web.core.auth.AuthenticationMode;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 可作为业务 Dropwizard {@code Configuration} 嵌套属性的 ddd4j Web 配置。
 */
@Getter
@Setter
public class Ddd4jDropwizardWebConfiguration {

    /**
     * 跳过默认认证拦截的公开路径配置。
     */
    private List<String> publicPaths = new ArrayList<>(Arrays.asList("/health", "/healthcheck/**"));
    /**
     * 未单独配置路径时采用的默认鉴权模式。
     */
    private AuthenticationMode defaultAuthenticationMode = AuthenticationMode.REQUIRED;
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
}
