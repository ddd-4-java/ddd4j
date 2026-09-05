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
package io.ddd4j.core.auth.event;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;

import java.time.Instant;
import java.util.Objects;

/**
 * 登录成功事件（通用鉴权事件）。
 *
 * <p>由具体 {@link io.ddd4j.core.subject.Subject} 实现在建立会话时发布。
 * 业务方可通过 {@link io.ddd4j.core.ddd.event.DomainEventPublisher} 订阅。
 *
 * <p>各框架适配层应负责把 ddd4j 通用事件桥接到本地事件总线：
 * <ul>
 *   <li>Spring：{@code SpringDomainEventPublisher} 解包 DomainEvent 后 publishEvent</li>
 *   <li>Quarkus：CDI {@code Event<LoginSucceededEvent>}</li>
 *   <li>Guice：Guava EventBus</li>
 *   <li>Javalin：业务方自定义</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public final class AuthSucceededEvent {
    private final AuthRequest request;
    private final AuthPrincipal principal;
    private final String token;
    private final Instant occurredAt;

    public AuthSucceededEvent(AuthRequest request, AuthPrincipal principal, String token, Instant occurredAt) {

        this.request = request;
        this.principal = principal;
        this.token = token;
        this.occurredAt = occurredAt;
    }

    public AuthRequest request() {
        return request;
    }

    public AuthPrincipal principal() {
        return principal;
    }

    public String token() {
        return token;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuthSucceededEvent)) return false;
        AuthSucceededEvent that = (AuthSucceededEvent) o;
        return Objects.equals(request, that.request) && Objects.equals(principal, that.principal) && Objects.equals(token, that.token) && Objects.equals(occurredAt, that.occurredAt);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(request);
result = 31 * result + request.hashCode();
        result = 31 * result + principal.hashCode();
        result = 31 * result + token.hashCode();
        result = 31 * result + occurredAt.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "AuthSucceededEvent{" + request + ", " + principal + ", " + token + ", " + occurredAt + '}';
    }

    public AuthRequest getRequest() {
        return request;
    }

    public AuthPrincipal getPrincipal() {
        return principal;
    }

    public String getToken() {
        return token;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
