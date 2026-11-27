package io.ddd4j.mq.delivery;

/**
 * 可靠投递的跨 Broker 标准消息头。
 *
 * <p>所有生产端都必须写入 {@link #MESSAGE_ID}；消费端据此实现 Inbox 去重。
 */
public final class MQDeliveryHeaders {

    /**
     * 稳定的业务消息标识，不能使用 broker 分配的瞬时投递标识替代。
     */
    public static final String MESSAGE_ID = "ddd4j-message-id";

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>MQDeliveryHeaders</code> \u5b9e\u4f8b\u3002 */
    private MQDeliveryHeaders() {
    }
}
