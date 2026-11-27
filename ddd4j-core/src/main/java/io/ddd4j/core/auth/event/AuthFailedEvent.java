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

import io.ddd4j.core.auth.AuthRequest;

import java.time.Instant;

/**
 * 登录失败事件（通用鉴权事件）。
 *
 * <p>由具体 {@link io.ddd4j.core.subject.Subject} 实现在登录校验失败时发布。
 * 业务方可通过 {@link io.ddd4j.core.ddd.event.DomainEventPublisher} 订阅。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public final class AuthFailedEvent {

    /** Java 序列化版本标识。 */
    private static final long serialVersionUID = 0L;

    /** \u83b7\u53d6\u767b\u5f55\u8bf7\u6c42\u3002 */
    private final AuthRequest request;

    /** \u83b7\u53d6\u767b\u5f55\u5931\u8d25\u539f\u56e0\u3002 */
    private final String reason;

    /** \u83b7\u53d6\u4e8b\u4ef6\u53d1\u751f\u65f6\u95f4\u3002 */
    private final Instant occurredAt;

    /**
     * 获取事件发生时间。
     *
     * @return 事件发生时间
     */
    public Instant getOccurredAt() {
        return occurredAt;
    }

    /**
     * 获取登录失败原因。
     *
     * @return 登录失败原因
     */
    public String getReason() {
        return reason;
    }

    /**
     * 获取登录请求。
     *
     * @return 登录请求
     */
    public AuthRequest getRequest() {
        return request;
    }

    /**
     * 创建AuthFailedEvent事件。
     *
     * @param request 登录请求
     * @param reason 登录失败原因
     * @param occurredAt 事件发生时间
     */
    @JsonCreator()
    public AuthFailedEvent(@JsonProperty("request") AuthRequest request, @JsonProperty("reason") String reason, @JsonProperty("occurredAt") Instant occurredAt) {
        this.request = request;
        this.reason = reason;
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
     * 获取用于 JSON 的登录失败原因。
     *
     * @return 用于 JSON 的登录失败原因
     */
    @JsonProperty("reason")
    public String reason() {
        return reason;
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

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        AuthFailedEvent other = (AuthFailedEvent) obj;
        return Objects.equals(this.request, other.request) && Objects.equals(this.reason, other.reason) && Objects.equals(this.occurredAt, other.occurredAt);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(request);
        result = 31 * result + Objects.hashCode(reason);
        result = 31 * result + Objects.hashCode(occurredAt);
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "AuthFailedEvent[request=" + request + ", reason=" + reason + ", occurredAt=" + occurredAt + "]";
    }
}
