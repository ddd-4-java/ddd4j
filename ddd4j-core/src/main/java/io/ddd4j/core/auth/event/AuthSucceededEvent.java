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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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
     * 获取登录请求。
     */
    private final AuthRequest request;

    /**
     * 获取认证主体。
     */
    private final AuthPrincipal principal;

    /**
     * 获取会话凭证。
     */
    private final String token;

    /**
     * 获取事件发生时间。
     */
    private final Instant occurredAt;

    /**
     * 创建AuthSucceededEvent事件。
     *
     * @param request    登录请求
     * @param principal  认证主体
     * @param token      会话凭证
     * @param occurredAt 事件发生时间
     */
    @JsonCreator
    public AuthSucceededEvent(@JsonProperty("request") AuthRequest request,
                              @JsonProperty("principal") AuthPrincipal principal,
                              @JsonProperty("token") String token,
                              @JsonProperty("occurredAt") Instant occurredAt) {

        this.request = request;
        this.principal = principal;
        this.token = token;
        this.occurredAt = occurredAt;
    }


    /**
     * 获取用于 JSON 的登录请求。
     *
     * @return 用于 JSON 的登录请求
     */
    @JsonProperty("request")
    public AuthRequest request() {
        return request;
    }

    /**
     * 获取用于 JSON 的认证主体。
     *
     * @return 用于 JSON 的认证主体
     */
    @JsonProperty("principal")
    public AuthPrincipal principal() {
        return principal;
    }

    /**
     * 获取用于 JSON 的会话凭证。
     *
     * @return 用于 JSON 的会话凭证
     */
    @JsonProperty("token")
    public String token() {
        return token;
    }

    /**
     * 获取用于 JSON 的事件发生时间。
     *
     * @return 用于 JSON 的事件发生时间
     */
    @JsonProperty("occurredAt")
    public Instant occurredAt() {
        return occurredAt;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AuthSucceededEvent)) {
            return false;
        }
        AuthSucceededEvent that = (AuthSucceededEvent) obj;
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
