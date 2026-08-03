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
