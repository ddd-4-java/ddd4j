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

    /** JWT \u7b7e\u540d\u5bc6\u94a5\u5141\u8bb8\u7684\u6700\u5c0f UTF-8 \u5b57\u8282\u6570\u3002 */
    private static final int MINIMUM_JWT_SECRET_BYTES = 32;

    /** \u5f53\u524d\u5e94\u7528\u9694\u79bb\u4ee4\u724c\u4f7f\u7528\u7684\u547d\u540d\u7a7a\u95f4\u3002 */
    private String tokenNamespace = "ddd4j";

    /** \u767b\u5f55\u4e0e\u6821\u9a8c\u4f7f\u7528\u7684 Sa-Token \u8d26\u53f7\u57df\u3002 */
    private String loginType = "login";

    /** \u8bbe\u7f6e\u4ee4\u724c\u6709\u6548\u671f\u7684\u79d2\u6570\u3002 */
    private long tokenTtlSeconds = 7_200L;

    /** \u4ee4\u724c\u8fde\u7eed\u65e0\u8bbf\u95ee\u65f6\u7684\u5931\u6548\u79d2\u6570\u3002 */
    private long activeTimeoutSeconds = -1L;

    /** \u5f53\u524d\u542f\u7528\u7684\u8ba4\u8bc1\u6a21\u5f0f\u3002 */
    private SaTokenAuthenticationMode authenticationMode = SaTokenAuthenticationMode.SESSION;

    /** \u7528\u4e8e\u7b7e\u53d1\u548c\u9a8c\u7b7e\u7684 JWT \u5bc6\u94a5\u914d\u7f6e\u3002 */
    private String jwtSecretKey;

    /** JWT \u4e2d\u7ea6\u5b9a\u7684\u7b7e\u53d1\u65b9\u3002 */
    private String jwtIssuer;

    /** JWT \u4e2d\u7ea6\u5b9a\u7684\u63a5\u6536\u65b9\u3002 */
    private String jwtAudience;

    /**
     * 返回 Sa-Token 的 token 名称，同时也是分布式存储 key 的命名空间前缀。
     * @return 返回的字符串内容
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

    /**
     * \u6821\u9a8c JWT \u5bc6\u94a5\u5b57\u8282\u957f\u5ea6\u548c\u5fc5\u586b\u7b7e\u53d1\u58f0\u660e\u3002
     * @throws IllegalArgumentException JWT \u914d\u7f6e\u4e0d\u6ee1\u8db3\u5bc6\u94a5\u957f\u5ea6\u6216\u58f0\u660e\u8981\u6c42
     */
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
