package io.ddd4j.extension.otel;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.delivery.MQDeliveryObserver;
import io.ddd4j.mq.delivery.MQOutboxRecord;

/**
 * 将可靠消息投递结果映射为 OpenTelemetry 指标的观察器。
 *
 * <p>Broker 名称由装配层提供，指标仅写入受控结果和 broker 标签；消息 ID、目的地、负载、
 * Header 与异常内容均不会进入指标属性。
 */
public final class OtelMQDeliveryObserver implements MQDeliveryObserver {

    private final String broker;

    /**
     * @param broker 低基数 broker 类型，例如 kafka 或 rabbitmq
     */
    public OtelMQDeliveryObserver(String broker) {
        this.broker = StrKit.isBlank(broker) ? "unknown" : StrKit.trim(broker);
    }

    @Override
    public void onOutboxPublished(MQOutboxRecord record) {
        MqDeliveryMetrics.outboxPublished(broker);
    }

    @Override
    public void onOutboxRetry(MQOutboxRecord record) {
        MqDeliveryMetrics.outboxRetry(broker);
    }

    @Override
    public void onOutboxDead(MQOutboxRecord record) {
        MqDeliveryMetrics.outboxDead(broker);
    }

    @Override
    public void onOutboxFailed(MQOutboxRecord record) {
        MqDeliveryMetrics.outboxFailed(broker);
    }

    @Override
    public void onInboxProcessed(String consumerId, String messageId) {
        MqDeliveryMetrics.inboxProcessed(broker);
    }

    @Override
    public void onInboxDuplicate(String consumerId, String messageId) {
        MqDeliveryMetrics.inboxDuplicate(broker);
    }

    @Override
    public void onInboxFailed(String consumerId, String messageId) {
        MqDeliveryMetrics.inboxFailed(broker);
    }
}
