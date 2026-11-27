package io.ddd4j.mq.message;

import io.ddd4j.mq.BrokerType;

import java.util.Optional;

/**
 * 消息确认端口。以 AMQP {@code Channel} 语义为基准；其他 Broker 由 Adapter 映射。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface Acknowledgment {

    /**
     * \u8fd4\u56de\u5f53\u524d\u6d88\u606f\u7684\u6295\u9012\u6807\u7b7e\u3002
     *
     * @return \u6295\u9012\u6807\u7b7e
     */
    long deliveryTag();

    /**
     * \u8fd4\u56de\u6d88\u606f ID\uff08\u82e5 Broker \u63d0\u4f9b\uff09\u3002
     *
     * @return \u6d88\u606f ID\uff0cBroker \u672a\u63d0\u4f9b\u65f6\u53ef\u80fd\u4e3a\u7a7a
     */
    String messageId();

    /**
     * \u8fd4\u56de\u5173\u8054 ID\uff08\u82e5 Broker \u63d0\u4f9b\uff09\u3002
     *
     * @return \u5173\u8054 ID\uff0cBroker \u672a\u63d0\u4f9b\u65f6\u53ef\u80fd\u4e3a\u7a7a
     */
    String correlationId();

    /**
     * \u5e95\u5c42\u8fde\u63a5/\u901a\u9053\u662f\u5426\u4ecd\u53ef\u7528\u3002
     *
     * @return \u8fde\u63a5\u6216\u901a\u9053\u53ef\u7528\u65f6\u4e3a true
     */
    boolean isOpen();

    /**
     * \u5f53\u524d\u6d88\u606f\u662f\u5426\u5df2\u88ab\u786e\u8ba4\u3002
     *
     * @return \u5df2\u786e\u8ba4\u65f6\u4e3a true
     */
    boolean isAcknowledged();

    /**
     * \u5f53\u524d\u786e\u8ba4\u5b9e\u73b0\u5bf9\u5e94\u7684 Broker \u7c7b\u578b\u3002
     *
     * @return Broker \u7c7b\u578b
     */
    BrokerType brokerType();

    /**
     * 确认单条消息成功消费（等价于 {@code basicAck(tag, false)}）。
     */
    void ack();

    /**
     * 确认消息，支持批量确认语义。
     *
     * @param multiple 是否批量确认至当前 tag
     */
    void ack(boolean multiple);

    /**
     * 否定确认，可控制是否重新入队。
     *
     * @param requeue 是否重新入队
     */
    void nack(boolean requeue);

    /**
     * 否定确认，支持批量与重入队控制。
     *
     * @param multiple 是否批量否定
     * @param requeue  是否重新入队
     */
    void nack(boolean multiple, boolean requeue);

    /**
     * 拒绝单条消息。
     *
     * @param requeue 是否重新入队
     */
    void reject(boolean requeue);

    /**
     * 恢复消息投递（Rabbit 专属语义，其他 Broker 可抛 {@link UnsupportedAckOperationException}）。
     *
     * @param requeue 是否重新入队
     */
    void recover(boolean requeue);

    /**
     * 确认单条消息的便捷方法。
     */
    default void ackSingle() {
        ack(false);
    }

    /**
     * 丢弃消息（不重新入队，通常进入 DLQ）。
     */
    default void discard() {
        nack(false);
    }

    /**
     * 将消息重新入队等待再次消费。
     */
    default void requeue() {
        nack(true);
    }

    /**
     * 通过 recover 语义重新入队。
     */
    default void requeueViaRecover() {
        recover(true);
    }

    /**
     * 获取底层 Broker 原生对象（如 {@code Channel}）。
     *
     * @param nativeType 原生类型
     * @param <T>        类型参数
     * @return 匹配的原生对象
     */
    <T> Optional<T> unwrap(Class<T> nativeType);
}
