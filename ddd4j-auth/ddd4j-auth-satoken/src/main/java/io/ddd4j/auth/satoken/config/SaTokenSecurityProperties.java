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
package io.ddd4j.auth.satoken.config;

import io.ddd4j.kit.lang.StrKit;
import lombok.Data;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * ddd4j 对 Sa-Token 的生产安全配置契约。
 *
 * <p>本对象不承担任何运行时框架的属性绑定；运行时适配层或应用应完成绑定后，在启动阶段交给
 * {@link SaTokenSecurityConfigurer} 一次性校验和安装。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Data
public class SaTokenSecurityProperties {

    private static final int MINIMUM_JWT_SECRET_BYTES = 32;

    private String tokenNamespace = "ddd4j";

    private String loginType = "login";

    private long tokenTtlSeconds = 7_200L;

    private long activeTimeoutSeconds = -1L;

    private SaTokenAuthenticationMode authenticationMode = SaTokenAuthenticationMode.SESSION;

    private String jwtSecretKey;

    private String jwtIssuer;

    private String jwtAudience;

    /**
     * 返回 Sa-Token 的 token 名称，同时也是分布式存储 key 的命名空间前缀。
     */
    public String tokenName() {
        return tokenNamespace + "-token";
    }

    /**
     * 校验生产启动所需的安全参数，任何不完整配置均拒绝启动。
     */
    public void validate() {
        if (StrKit.isBlank(tokenNamespace) || !tokenNamespace.matches("[A-Za-z][A-Za-z0-9-]{0,63}")) {
            throw new IllegalArgumentException("tokenNamespace must match [A-Za-z][A-Za-z0-9-]{0,63}");
        }
        if (StrKit.isBlank(loginType) || !loginType.matches("[A-Za-z][A-Za-z0-9-]{0,63}")) {
            throw new IllegalArgumentException("loginType must match [A-Za-z][A-Za-z0-9-]{0,63}");
        }
        if (tokenTtlSeconds <= 0L) {
            throw new IllegalArgumentException("tokenTtlSeconds must be greater than zero");
        }
        if (activeTimeoutSeconds != -1L && activeTimeoutSeconds <= 0L) {
            throw new IllegalArgumentException("activeTimeoutSeconds must be -1 or greater than zero");
        }
        if (Objects.isNull(authenticationMode)) {
            throw new IllegalArgumentException("authenticationMode must not be null");
        }
        if (authenticationMode == SaTokenAuthenticationMode.JWT_SIMPLE) {
            validateJwtConfiguration();
        }
    }

    private void validateJwtConfiguration() {
        if (StrKit.isBlank(jwtSecretKey)
                || jwtSecretKey.getBytes(StandardCharsets.UTF_8).length < MINIMUM_JWT_SECRET_BYTES) {
            throw new IllegalArgumentException("jwtSecretKey must contain at least 32 UTF-8 bytes");
        }
        if (StrKit.isBlank(jwtIssuer)) {
            throw new IllegalArgumentException("jwtIssuer must not be blank in JWT_SIMPLE mode");
        }
        if (StrKit.isBlank(jwtAudience)) {
            throw new IllegalArgumentException("jwtAudience must not be blank in JWT_SIMPLE mode");
        }
    }
}
