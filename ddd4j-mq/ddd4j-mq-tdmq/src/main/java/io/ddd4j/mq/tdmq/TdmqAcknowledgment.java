package io.ddd4j.mq.tdmq;

import io.ddd4j.mq.BrokerType;
import io.ddd4j.mq.message.Acknowledgment;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 腾讯云 TDMQ 消息确认实现。
 *
 * <p>TDMQ 是腾讯云基于 Pulsar 的托管服务。{@link #ackCallback} 暴露由业务侧 TDMQ 客户端传入的 ack 回调，
 * 由 ddd4j-MQ 的 consume 流程根据执行结果决定 ack/nack/requeue。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public final class TdmqAcknowledgment implements Acknowledgment {

    /** \u6d88\u606f\u6807\u8bc6\u3002 */
    private final String messageId;
    /** \u7528\u4e8e\u5173\u8054\u540c\u4e00\u6b21\u64cd\u4f5c\u4ea7\u751f\u7684\u4e8b\u4ef6\u6216\u6d88\u606f\u7684\u6807\u8bc6\u3002 */
    private final String correlationId;
    /** \u7528\u4e8e\u5e95\u5c42\u4ee3\u7406\u786e\u8ba4\u6d88\u606f\u7684\u6295\u9012\u6807\u8bb0\u3002 */
    private final long deliveryTag;
    /** \u539f\u751f\u4ee3\u7406\u66b4\u9732\u7684\u6295\u9012\u786e\u8ba4\u56de\u8c03\u3002 */
    private final java.util.function.Consumer<Boolean> ackCallback;
    /** \u8bb0\u5f55\u5df2\u786e\u8ba4\u6d88\u606f\u7684\u72b6\u6001\u6216\u7ed3\u679c\u3002 */
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    /**
     * \u521b\u5efa TDMQ \u6d88\u606f\u786e\u8ba4\u53e5\u67c4\u3002
     * @param messageId \u6d88\u606f\u6807\u8bc6
     * @param correlationId \u5173\u8054\u6807\u8bc6
     * @param deliveryTag \u6295\u9012\u6807\u7b7e
     * @param ackCallback \u786e\u8ba4\u7ed3\u679c\u56de\u8c03
     */
    public TdmqAcknowledgment(String messageId,
                              String correlationId,
                              long deliveryTag,
                              java.util.function.Consumer<Boolean> ackCallback) {
        this.messageId = messageId;
        this.correlationId = correlationId;
        this.deliveryTag = deliveryTag;
        this.ackCallback = Objects.requireNonNull(ackCallback, "ackCallback");
    }

    /**
     * 返回本次消息投递的标识。
     *
     * @return 当前投递的标记值
     */
    @Override
    public long deliveryTag() {
        return deliveryTag;
    }

    /**
     * 返回收到的消息标识。
     *
     * @return 当前消息的标识
     */
    @Override
    public String messageId() {
        return messageId;
    }

    /**
     * 返回消息所属链路的关联标识。
     *
     * @return 当前消息关联的请求或事件标识
     */
    @Override
    public String correlationId() {
        return correlationId;
    }

    /**
     * 检查当前投递的确认对象是否仍可用。
     *
     * <p>此实现调用 <code>acknowledged.get()</code>。</p>
     *
     * @return 实际返回<code>!acknowledged.get()</code>
     */
    @Override
    public boolean isOpen() {
        return !acknowledged.get();
    }

    /**
     * 判断当前投递是否已完成确认处理。
     *
     * <p>此实现调用 <code>acknowledged.get()</code>。</p>
     *
     * @return 此投递已经进入确认终态时为真
     */
    @Override
    public boolean isAcknowledged() {
        return acknowledged.get();
    }

    /**
     * 返回当前投递所使用的消息中间件类型。
     *
     * @return 客户端或投递协议对应的 broker 枚举值
     */
    @Override
    public BrokerType brokerType() {
        return BrokerType.TDMQ;
    }

    /**
     * 确认本次投递或将已处理的位置通知 broker。
     *
     * <p>此实现调用 <code>ack(false)</code>。</p>
     */
    @Override
    public void ack() {
        ack(false);
    }

    /**
     * 通过底层消息会话更新当前投递的确认状态。
     *
     * @param multiple 是否批量确认至当前 tag
     */
    @Override
    public void ack(boolean multiple) {
        ensureNotAcknowledged();
        ackCallback.accept(true);
        acknowledged.set(true);
        log.debug("TDMQ ack messageId={}, multiple={}", messageId, multiple);
    }

    /**
     * 向底层客户端报告本次投递处理失败，并按调用选项决定是否请求重新投递。
     *
     * <p>此实现调用 <code>nack(false, requeue)</code>。</p>
     *
     * @param requeue 是否重新入队
     */
    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    /**
     * 通过底层消息会话更新当前投递的确认状态。
     *
     * @param multiple 是否批量否定
     * @param requeue 是否重新入队
     */
    @Override
    public void nack(boolean multiple, boolean requeue) {
        ensureNotAcknowledged();
        ackCallback.accept(requeue);
        acknowledged.set(true);
        log.debug("TDMQ nack messageId={}, requeue={}, multiple={}", messageId, requeue, multiple);
    }

    /**
     * 拒绝当前投递，并按约定处理是否重新入队。
     *
     * <p>此实现调用 <code>nack(requeue)</code>。</p>
     *
     * @param requeue 是否重新入队
     */
    @Override
    public void reject(boolean requeue) {
        nack(requeue);
    }

    /**
     * 请求底层消息会话恢复未处理的投递。
     *
     * <p>此实现调用 <code>nack(requeue)</code>。</p>
     *
     * @param requeue 是否重新入队
     */
    @Override
    public void recover(boolean requeue) {
        nack(requeue);
    }

    /**
     * 取出适配器持有的底层投递或客户端对象。
     *
     * <p>此实现调用 <code>nativeType.isInstance(this)</code>。</p>
     *
     * @param <T> 类型参数
     * @param nativeType 原生类型
     * @return 根据执行路径返回<code>Optional.of(nativeType.cast(this))</code>、<code>Optional.empty()</code>
     */
    @Override
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        Objects.requireNonNull(nativeType, "nativeType");
        if (nativeType.isInstance(this)) {
            return Optional.of(nativeType.cast(this));
        }
        return Optional.empty();
    }

    /** \u62d2\u7edd\u91cd\u590d\u786e\u8ba4\u540c\u4e00\u6761 TDMQ \u6295\u9012\u6d88\u606f\u3002 */
    private void ensureNotAcknowledged() {
        if (acknowledged.get()) {
            throw new IllegalStateException("TDMQ message already acknowledged, messageId=" + messageId);
        }
    }
}
