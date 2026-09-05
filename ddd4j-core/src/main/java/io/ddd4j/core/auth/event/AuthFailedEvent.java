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

import io.ddd4j.core.auth.AuthRequest;

import java.time.Instant;
import java.util.Objects;

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
    private final AuthRequest request;
    private final String reason;
    private final Instant occurredAt;

    public AuthFailedEvent(AuthRequest request, String reason, Instant occurredAt) {

        this.request = request;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    public AuthRequest request() {
        return request;
    }

    public String reason() {
        return reason;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuthFailedEvent)) return false;
        AuthFailedEvent that = (AuthFailedEvent) o;
        return Objects.equals(request, that.request) && Objects.equals(reason, that.reason) && Objects.equals(occurredAt, that.occurredAt);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(request);
result = 31 * result + request.hashCode();
        result = 31 * result + reason.hashCode();
        result = 31 * result + occurredAt.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "AuthFailedEvent{" + request + ", " + reason + ", " + occurredAt + '}';
    }

    public AuthRequest getRequest() {
        return request;
    }

    public String getReason() {
        return reason;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
