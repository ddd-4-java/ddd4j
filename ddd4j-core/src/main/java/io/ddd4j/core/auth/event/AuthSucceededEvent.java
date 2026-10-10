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
import lombok.Getter;

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
@Getter
public final class AuthSucceededEvent {
    /**
     * 登录请求。
     */
    private final AuthRequest request;
    /**
     * 登录凭证。
     */
    private final AuthPrincipal principal;
    /**
     * 登录令牌。
     */
    private final String token;
    /**
     * 登录时间。
     */
    private final Instant occurredAt;

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
 * @param request 请求对象
 * @param principal 认证主体
 * @param token 令牌
 * @param occurredAt 发生时间
     * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
     * @since 3.0.0
     */
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
        result = 31 * result + Objects.hashCode(principal);
        result = 31 * result + Objects.hashCode(token);
        result = 31 * result + Objects.hashCode(occurredAt);
        return result;
    }

    @Override
    public String toString() {
        return "AuthSucceededEvent[request=" + request + ", principal=" + principal + ", token=" + token + ", occurredAt=" + occurredAt + ']';
    }

}
