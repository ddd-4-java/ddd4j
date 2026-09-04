package io.ddd4j.sample.order.application;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;

/**
 * 可靠 Outbox 发布协调器。
 *
 * <p>消息只有在传输端口成功返回后才会确认；失败消息记录原因并保留给下一轮调度重试。
 */
@Slf4j
public final class OutboxPublisher {

    private final OutboxPort outbox;
    private final IntegrationEventPublisher publisher;

    public OutboxPublisher(OutboxPort outbox, IntegrationEventPublisher publisher) {
        this.outbox = Objects.requireNonNull(outbox, "outbox must not be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
    }

    public int publishPending(int limit) {
        return dispatchPending(limit).published();
    }

    /**
     * 发布至多 {@code limit} 条待处理消息。
     *
     * @param limit 本轮最多处理数量
     * @return 本轮发布结果
     */
    public OutboxDispatchResult dispatchPending(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }
        List<OutboxMessage> messages = outbox.pending(limit);
        int published = 0;
        int failed = 0;
        for (OutboxMessage message : messages) {
            try {
                publisher.publish(message);
                outbox.markPublished(message.id());
                published++;
            } catch (RuntimeException exception) {
                failed++;
                outbox.markFailed(message.id(), exception.getMessage());
                log.warn("Outbox message {} remains pending after publish failure", message.id(), exception);
            }
        }
        return new OutboxDispatchResult(messages.size(), published, failed);
    }
}
