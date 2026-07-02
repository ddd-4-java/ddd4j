package io.ddd4j.mq.ack;

import io.ddd4j.mq.registry.MQBrokerType;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 无操作消息确认器：用于单测或占位，所有写操作仅记录日志不触达 Broker。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class NoOpMessageAcknowledgment implements MessageAcknowledgment {

    /**
     * 单例占位确认器（无 Broker 场景）。
     */
    public static final NoOpMessageAcknowledgment INSTANCE = new NoOpMessageAcknowledgment();

    private final long deliveryTag;
    private final String messageId;
    private final String correlationId;
    private final MQBrokerType brokerType;
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);
    private final Object nativeHandle;

    /**
     * 使用默认元数据构建。
     */
    public NoOpMessageAcknowledgment() {
        this(AcknowledgmentContext.builder().build());
    }

    /**
     * 基于 {@link AcknowledgmentContext} 构建。
     *
     * @param context 确认上下文
     */
    public NoOpMessageAcknowledgment(AcknowledgmentContext context) {
        this.deliveryTag = context.getDeliveryTag();
        this.messageId = context.getMessageId();
        this.correlationId = context.getCorrelationId();
        this.brokerType = context.getBrokerType();
        this.nativeHandle = context.getNativeHandle();
        if (context.isAcknowledged()) {
            this.acknowledged.set(true);
        }
    }

    @Override
    public long deliveryTag() {
        return deliveryTag;
    }

    @Override
    public String messageId() {
        return messageId;
    }

    @Override
    public String correlationId() {
        return correlationId;
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public boolean isAcknowledged() {
        return acknowledged.get();
    }

    @Override
    public MQBrokerType brokerType() {
        return brokerType;
    }

    @Override
    public void ack() {
        markAcknowledged("ack");
    }

    @Override
    public void ack(boolean multiple) {
        markAcknowledged(multiple ? "ack(multiple=true)" : "ack(multiple=false)");
    }

    @Override
    public void nack(boolean requeue) {
        markAcknowledged("nack(requeue=" + requeue + ")");
    }

    @Override
    public void nack(boolean multiple, boolean requeue) {
        markAcknowledged("nack(multiple=" + multiple + ",requeue=" + requeue + ")");
    }

    @Override
    public void reject(boolean requeue) {
        markAcknowledged("reject(requeue=" + requeue + ")");
    }

    @Override
    public void recover(boolean requeue) {
        markAcknowledged("recover(requeue=" + requeue + ")");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        if (Objects.nonNull(nativeHandle) && nativeType.isInstance(nativeHandle)) {
            return Optional.of((T) nativeHandle);
        }
        return Optional.empty();
    }

    /**
     * 标记已确认并输出 debug 日志（单测场景可忽略）。
     */
    private void markAcknowledged(String operation) {
        if (acknowledged.compareAndSet(false, true)) {
            log.debug("NoOpMessageAcknowledgment: {}", operation);
        }
    }
}
