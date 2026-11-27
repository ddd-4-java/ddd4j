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

    /** \u7528\u4e8e\u533a\u5206\u6d88\u606f\u6295\u9012\u6307\u6807\u6765\u6e90\u7684\u4ee3\u7406\u540d\u79f0\u3002 */
    private final String broker;

    /**
     * 初始化 <code>OtelMQDeliveryObserver</code> 使用的 <code>broker</code>。
     *
     * @param broker \u4f4e\u57fa\u6570 broker \u7c7b\u578b\uff0c\u4f8b\u5982 kafka \u6216 rabbitmq
     */
    public OtelMQDeliveryObserver(String broker) {
        this.broker = StrKit.isBlank(broker) ? "unknown" : StrKit.trim(broker);
    }

    /**
     * Outbox 消息已由持有租约的实例确认发布。
     *
     * <p>此实现调用 <code>MqDeliveryMetrics.outboxPublished(broker)</code>。</p>
     *
     * @param record 已发布记录
     */
    @Override
    public void onOutboxPublished(MQOutboxRecord record) {
        MqDeliveryMetrics.outboxPublished(broker);
    }

    /**
     * Outbox 消息已安排后续重试。
     *
     * <p>此实现调用 <code>MqDeliveryMetrics.outboxRetry(broker)</code>。</p>
     *
     * @param record 投递失败记录
     */
    @Override
    public void onOutboxRetry(MQOutboxRecord record) {
        MqDeliveryMetrics.outboxRetry(broker);
    }

    /**
     * Outbox 消息已超过重试上限并进入死信状态。
     *
     * <p>此实现调用 <code>MqDeliveryMetrics.outboxDead(broker)</code>。</p>
     *
     * @param record 死信记录
     */
    @Override
    public void onOutboxDead(MQOutboxRecord record) {
        MqDeliveryMetrics.outboxDead(broker);
    }

    /**
     * Outbox 发送或状态确认失败，最终状态尚未确定。
     *
     * <p>此实现调用 <code>MqDeliveryMetrics.outboxFailed(broker)</code>。</p>
     *
     * @param record 投递状态不确定的记录
     */
    @Override
    public void onOutboxFailed(MQOutboxRecord record) {
        MqDeliveryMetrics.outboxFailed(broker);
    }

    /**
     * Inbox 首次成功处理消息。
     *
     * <p>此实现调用 <code>MqDeliveryMetrics.inboxProcessed(broker)</code>。</p>
     *
     * @param consumerId 消费者标识
     * @param messageId 稳定消息标识
     */
    @Override
    public void onInboxProcessed(String consumerId, String messageId) {
        MqDeliveryMetrics.inboxProcessed(broker);
    }

    /**
     * Inbox 识别到已处理的重复消息。
     *
     * <p>此实现调用 <code>MqDeliveryMetrics.inboxDuplicate(broker)</code>。</p>
     *
     * @param consumerId 消费者标识
     * @param messageId 稳定消息标识
     */
    @Override
    public void onInboxDuplicate(String consumerId, String messageId) {
        MqDeliveryMetrics.inboxDuplicate(broker);
    }

    /**
     * Inbox 记录或业务处理失败，调用方应保持不 ACK 以触发重投。
     *
     * <p>此实现调用 <code>MqDeliveryMetrics.inboxFailed(broker)</code>。</p>
     *
     * @param consumerId 消费者标识
     * @param messageId 稳定消息标识
     */
    @Override
    public void onInboxFailed(String consumerId, String messageId) {
        MqDeliveryMetrics.inboxFailed(broker);
    }
}
