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
package io.ddd4j.web.core.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.Contexts;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.web.core.error.WebStatusException;

import java.util.Objects;
import java.util.Optional;

/**
 * 将标准 Bearer Token 委托给当前运行时注册的 Subject SPI。
 */
public final class BearerSubjectAuthenticator {

    /** \u4ece HTTP Authorization \u5934\u8bfb\u53d6 Bearer \u4ee4\u724c\u7684\u7ec4\u4ef6\u3002 */
    private final BearerTokenResolver tokenResolver;

    /**
     * 使用默认 {@link BearerTokenResolver} 构造认证器。
     */
    public BearerSubjectAuthenticator() {
        this(new BearerTokenResolver());
    }

    /**
     * 使用指定的 Bearer Token 解析器构造认证器。
     *
     * @param tokenResolver Bearer Token 解析器，不可为 {@code null}
     */
    public BearerSubjectAuthenticator(BearerTokenResolver tokenResolver) {
        this.tokenResolver = Objects.requireNonNull(tokenResolver, "tokenResolver must not be null");
    }

    /**
     * 认证 {@code Authorization} 头并返回认证主体。
     *
     * @param authorization 原始 Authorization 头值
     * @return 认证通过的主体
     * @throws WebStatusException 缺少 Bearer Token、Subject SPI 不可用或令牌无效时抛出
     */
    public AuthPrincipal authenticate(String authorization) {
        return authenticateSubject(authorization).principal();
    }

    /**
     * 认证 {@code Authorization} 头并返回完整认证结果。
     *
     * @param authorization 原始 Authorization 头值
     * @return 包含令牌、主体与 Subject SPI 的认证结果
     * @throws WebStatusException 缺少 Bearer Token、Subject SPI 不可用或令牌无效时抛出
     */
    public Authentication authenticateSubject(String authorization) {
        String token = tokenResolver.resolve(authorization)
                .orElseThrow(() -> new WebStatusException(401, "Bearer token is required"));
        return authenticateToken(token);
    }

    /**
     * 解析 Authorization 头，并验证解析出的 Bearer Token。
     *
     * @param authorization 原始 Authorization 头值
     * @return 认证结果。如果未解析出 Bearer Token，返回 {@link Optional#empty()}
     * @throws WebStatusException 已解析出令牌且 Subject SPI 不可用，或令牌无效或过期时抛出
     */
    public Optional<Authentication> authenticateOptional(String authorization) {
        return tokenResolver.resolve(authorization).map(this::authenticateToken);
    }

    /**
     * \u6821\u9a8c Bearer \u4ee4\u724c\u5e76\u89e3\u6790\u5f53\u524d\u8bf7\u6c42\u4e3b\u4f53\u3002
     * @param token \u4ece Authorization \u8bfb\u53d6\u7684 Bearer \u8bbf\u95ee\u4ee4\u724c
     * @return \u4ee4\u724c\u6709\u6548\u65f6\u7684\u8ba4\u8bc1\u7ed3\u679c
     */
    private Authentication authenticateToken(String token) {
        SubjectProvider provider = Contexts.get(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class)
                .orElseThrow(() -> new WebStatusException(401, "Subject provider is unavailable"));
        Subject subject = provider.getSubject();
        AuthPrincipal principal = subject.verify(token);
        if (Objects.isNull(principal)) {
            throw new WebStatusException(401, "Bearer token is invalid or expired");
        }
        return new Authentication(token, principal, subject);
    }

    /**
     * 认证结果：令牌、认证主体与签发该主体的 Subject SPI。
     */
    public final static class Authentication {

        /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
        private static final long serialVersionUID = 0L;

        /** \u5df2\u4ece\u5f53\u524d\u8bf7\u6c42\u8bfb\u51fa\u5e76\u6821\u9a8c\u7684\u8ba4\u8bc1\u4ee4\u724c\u3002 */
        private final String token;

        /** \u5f53\u524d\u8bf7\u6c42\u8ba4\u8bc1\u540e\u7684\u4e1a\u52a1\u4e3b\u4f53\u4fe1\u606f\u3002 */
        private final AuthPrincipal principal;

        /** \u5f53\u524d\u8bf7\u6c42\u6240\u7ed1\u5b9a\u7684\u4e3b\u4f53\u64cd\u4f5c\u5165\u53e3\u3002 */
        private final Subject subject;

        /**
         * 获取通过校验的 Bearer Token。
         *
         * @return 令牌值
         */
        public String getToken() {
            return token;
        }

        /**
         * 获取认证通过的主体。
         *
         * @return 认证主体
         */
        public AuthPrincipal getPrincipal() {
            return principal;
        }

        /**
         * 获取完成校验的 Subject SPI。
         *
         * @return Subject 实例
         */
        public Subject getSubject() {
            return subject;
        }

        /**
         * 构造认证结果。
         *
         * @param token     通过校验的 Bearer Token
         * @param principal 认证通过的主体
         * @param subject   完成校验的 Subject SPI
         */
        @JsonCreator()
        public Authentication(@JsonProperty("token") String token, @JsonProperty("principal") AuthPrincipal principal, @JsonProperty("subject") Subject subject) {
            this.token = token;
            this.principal = principal;
            this.subject = subject;
        }

        /**
         * 返回身份令牌。
         *
         * @return 身份令牌
         */
        @JsonProperty("token")
        public String token() {
            return token;
        }

        /**
         * 返回认证主体。
         *
         * @return 认证主体
         */
        @JsonProperty("principal")
        public AuthPrincipal principal() {
            return principal;
        }

        /**
         * 返回Subject 实例。
         *
         * @return Subject 实例
         */
        @JsonProperty("subject")
        public Subject subject() {
            return subject;
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            Authentication other = (Authentication) obj;
            return Objects.equals(this.token, other.token) && Objects.equals(this.principal, other.principal) && Objects.equals(this.subject, other.subject);
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(token);
            result = 31 * result + Objects.hashCode(principal);
            result = 31 * result + Objects.hashCode(subject);
            return result;
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return "Authentication[token=" + token + ", principal=" + principal + ", subject=" + subject + "]";
        }
    }
}
