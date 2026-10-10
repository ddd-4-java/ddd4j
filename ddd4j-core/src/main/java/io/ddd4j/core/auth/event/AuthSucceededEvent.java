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

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;

import java.time.Instant;

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

    private static final long serialVersionUID = 0L;

    private final AuthRequest request;

    private final AuthPrincipal principal;

    private final String token;

    private final Instant occurredAt;

    @JsonCreator()
    public AuthSucceededEvent(@JsonProperty("request") AuthRequest request, @JsonProperty("principal") AuthPrincipal principal, @JsonProperty("token") String token, @JsonProperty("occurredAt") Instant occurredAt) {
        this.request = request;
        this.principal = principal;
        this.token = token;
        this.occurredAt = occurredAt;
    }

    @JsonProperty("request")
    public AuthRequest request() {
        return request;
    }

    @JsonProperty("principal")
    public AuthPrincipal principal() {
        return principal;
    }

    @JsonProperty("token")
    public String token() {
        return token;
    }

    @JsonProperty("occurredAt")
    public Instant occurredAt() {
        return occurredAt;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        AuthSucceededEvent other = (AuthSucceededEvent) obj;
        return Objects.equals(this.request, other.request) && Objects.equals(this.principal, other.principal) && Objects.equals(this.token, other.token) && Objects.equals(this.occurredAt, other.occurredAt);
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(request);
        result = 31 * result + Objects.hashCode(principal);
        result = 31 * result + Objects.hashCode(token);
        result = 31 * result + Objects.hashCode(occurredAt);
        return result;
    }

    @Override
    public String toString() {
        return "AuthSucceededEvent[request=" + request + ", principal=" + principal + ", token=" + token + ", occurredAt=" + occurredAt + "]";
    }
}
