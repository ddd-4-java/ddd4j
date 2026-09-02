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
 *   <li>Guice：Guava EventBus</li>
 *   <li>Javalin：业务方自定义</li>
 * </ul>
 *
 * <p>1.0.x（JDK8）实现说明：3.0.x 中本类为 record，JDK8 无 record 语法，
 * 降级为 final class + 手写 accessor/equals/hashCode/toString，语义保持一致。
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
        if (this == o) {
            return true;
        }
        if (!(o instanceof AuthSucceededEvent)) {
            return false;
        }
        AuthSucceededEvent that = (AuthSucceededEvent) o;
        return Objects.equals(request, that.request)
                && Objects.equals(principal, that.principal)
                && Objects.equals(token, that.token)
                && Objects.equals(occurredAt, that.occurredAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(request, principal, token, occurredAt);
    }

    @Override
    public String toString() {
        return "AuthSucceededEvent[request=" + request + ", principal=" + principal
                + ", token=" + token + ", occurredAt=" + occurredAt + "]";
    }
}
