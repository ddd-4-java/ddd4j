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

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.Contexts;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;

import java.util.Objects;
import java.util.Optional;
import io.ddd4j.web.core.error.WebStatusException;

/**
 * 将标准 Bearer Token 委托给当前运行时注册的 Subject SPI。
 */
public final class BearerSubjectAuthenticator {

    private final BearerTokenResolver tokenResolver;

    public BearerSubjectAuthenticator() {
        this(new BearerTokenResolver());
    }

    public BearerSubjectAuthenticator(BearerTokenResolver tokenResolver) {
        this.tokenResolver = Objects.requireNonNull(tokenResolver, "tokenResolver must not be null");
    }

    public AuthPrincipal authenticate(String authorization) {
        return authenticateSubject(authorization).principal();
    }

    public Authentication authenticateSubject(String authorization) {
        String token = tokenResolver.resolve(authorization)
                .orElseThrow(() -> new WebStatusException(401, "Bearer token is required"));
        return authenticateToken(token);
    }

    public Optional<Authentication> authenticateOptional(String authorization) {
        return tokenResolver.resolve(authorization).map(this::authenticateToken);
    }

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

    public static final class Authentication {
        private final String token;
        private final AuthPrincipal principal;
        private final Subject subject;
        public Authentication(String token, AuthPrincipal principal, Subject subject) { this.token=token; this.principal=principal; this.subject=subject; }
        public String token() { return token; }
        public AuthPrincipal principal() { return principal; }
        public Subject subject() { return subject; }
        @Override public boolean equals(Object o) { return this==o || (o instanceof Authentication && java.util.Objects.equals(token,((Authentication)o).token()) && java.util.Objects.equals(principal,((Authentication)o).principal()) && java.util.Objects.equals(subject,((Authentication)o).subject())); }
        @Override public int hashCode() { return java.util.Objects.hash(token,principal,subject); }
        @Override public String toString() { return "Authentication{token="+token+"}"; }
    }
}
