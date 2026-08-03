package io.ddd4j.mq.delivery;

import io.ddd4j.kit.lang.StrKit;

import java.time.Instant;
import java.util.Objects;

/**
 * 将 Inbox 去重和消费者业务处理串联的协调器。
 *
 * <p>调用此类的方法与业务处理器必须由同一个事务拦截器包裹：首次记录和业务写入一起提交，
 * 处理器抛异常时一起回滚，重复消息则不再执行业务处理但仍应由调用方 ACK。
 */
public final class MQInboxProcessor {

    private final MQInboxStore store;
    private final String consumerId;

    public MQInboxProcessor(MQInboxStore store, String consumerId) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        if (StrKit.isBlank(consumerId)) {
            throw new IllegalArgumentException("consumerId must not be blank");
        }
        this.consumerId = consumerId;
    }

    /**
     * 处理一条可靠消息。
     *
     * @param messageId 生产端稳定消息标识
     * @param processedAt 当前处理时间
     * @param handler 业务处理器
     * @return {@code true} 表示首次处理，{@code false} 表示重复消息
     */
    public boolean process(String messageId, Instant processedAt, Runnable handler) {
        if (StrKit.isBlank(messageId)) {
            throw new IllegalArgumentException("messageId must not be blank");
        }
        Objects.requireNonNull(processedAt, "processedAt must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
        if (!store.recordIfAbsent(consumerId, messageId, processedAt)) {
            return false;
        }
        handler.run();
        return true;
    }
}
