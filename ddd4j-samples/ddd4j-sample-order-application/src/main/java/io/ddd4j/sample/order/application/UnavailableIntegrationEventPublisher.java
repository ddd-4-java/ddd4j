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
package io.ddd4j.sample.order.application;

import java.util.Objects;

/**
 * 保持 Outbox 消息待重试的不可用传输端口。
 *
 * <p>该实现不吞掉消息：每次发布都抛出异常，使 {@link OutboxPublisher} 记录失败并保留消息，适合 broker 尚未
 * 配置或临时不可用时的安全降级。
 */
public final class UnavailableIntegrationEventPublisher implements IntegrationEventPublisher {

    private final String reason;

    public UnavailableIntegrationEventPublisher(String reason) {
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    @Override
    public void publish(OutboxMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        throw new IllegalStateException(reason);
    }
}
