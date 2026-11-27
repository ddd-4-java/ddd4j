package io.ddd4j.mq.rocketmq;

import io.ddd4j.mq.BrokerType;
import io.ddd4j.mq.message.Acknowledgment;
import org.apache.rocketmq.common.message.MessageExt;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RocketMQ acknowledgment state adapter.
 *
 * <p>RocketMQ returns consume status from listener instead of exposing broker-level ack calls.
 * This adapter records whether ddd4j wants the message consumed successfully or retried.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class RocketAcknowledgment implements Acknowledgment {

    /** \u539f\u751f {@link MessageExt} \u5728 unwrap \u65f6\u4f7f\u7528\u7684\u7c7b\u578b\u6807\u8bc6\u5934\u952e\u3002 */
    public static final String HEADER_ROCKET_MESSAGE = "ddd4j.rocket.message";

    /** \u5f53\u524d\u6d88\u8d39\u786e\u8ba4\u6240\u5173\u8054\u7684\u539f\u751f\u4ee3\u7406\u6d88\u606f\u3002 */
    private final MessageExt message;
    /** \u8bb0\u5f55\u5df2\u786e\u8ba4\u6d88\u606f\u7684\u72b6\u6001\u6216\u7ed3\u679c\u3002 */
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);
    /** \u5f53\u524d RocketMQ \u6d88\u8d39\u7ed3\u679c\u662f\u5426\u8981\u6c42\u7531\u4ee3\u7406\u518d\u6b21\u6295\u9012\u3002 */
    private volatile boolean reconsume;

    /**
     * \u57fa\u4e8e\u539f\u751f\u6d88\u606f\u6784\u9020 ack \u72b6\u6001\u9002\u914d\u5668\u3002
     * @param message \u539f\u751f RocketMQ \u6d88\u606f\uff0c\u4e0d\u53ef\u4e3a\u7a7a
     */
    public RocketAcknowledgment(MessageExt message) {
        this.message = Objects.requireNonNull(message, "message");
    }

    /**
     * 返回本次消息投递的标识。
     *
     * <p>此实现调用 <code>message.getQueueOffset()</code>。</p>
     *
     * @return 当前投递的标记值
     */
    @Override
    public long deliveryTag() {
        return message.getQueueOffset();
    }

    /**
     * 返回收到的消息标识。
     *
     * <p>此实现调用 <code>message.getMsgId()</code>。</p>
     *
     * @return 当前消息的标识
     */
    @Override
    public String messageId() {
        return message.getMsgId();
    }

    /**
     * 返回消息所属链路的关联标识。
     *
     * <p>此实现调用 <code>message.getKeys()</code>。</p>
     *
     * @return 当前消息关联的请求或事件标识
     */
    @Override
    public String correlationId() {
        return message.getKeys();
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
        return BrokerType.ROCKET;
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
     * 确认本次投递或将已处理的位置通知 broker。
     *
     * <p>此实现调用 <code>complete(false)</code>。</p>
     *
     * @param multiple 是否批量确认至当前 tag
     */
    @Override
    public void ack(boolean multiple) {
        complete(false);
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
     * 向底层客户端报告本次投递处理失败，并按调用选项决定是否请求重新投递。
     *
     * <p>此实现调用 <code>complete(requeue)</code>。</p>
     *
     * @param multiple 是否批量否定
     * @param requeue 是否重新入队
     */
    @Override
    public void nack(boolean multiple, boolean requeue) {
        complete(requeue);
    }

    /**
     * 拒绝当前投递，并按约定处理是否重新入队。
     *
     * <p>此实现调用 <code>complete(requeue)</code>。</p>
     *
     * @param requeue 是否重新入队
     */
    @Override
    public void reject(boolean requeue) {
        complete(requeue);
    }

    /**
     * 请求底层消息会话恢复未处理的投递。
     *
     * <p>此实现调用 <code>complete(requeue)</code>。</p>
     *
     * @param requeue 是否重新入队
     */
    @Override
    public void recover(boolean requeue) {
        complete(requeue);
    }

    /**
     * 取出适配器持有的底层投递或客户端对象。
     *
     * <p>此实现调用 <code>Optional.empty()</code>。</p>
     *
     * @param <T> 类型参数
     * @param nativeType 原生类型
     * @return 根据执行路径返回<code>Optional.empty()</code>、<code>Optional.of(nativeType.cast(message))</code>、<code>Optional.of(nativeType.cast(this))</code>
     */
    @Override
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        if (Objects.isNull(nativeType)) {
            return Optional.empty();
        }
        if (nativeType.isInstance(message)) {
            return Optional.of(nativeType.cast(message));
        }
        if (nativeType.isInstance(this)) {
            return Optional.of(nativeType.cast(this));
        }
        return Optional.empty();
    }

    /**
     * \u662f\u5426\u8981\u6c42\u7a0d\u540e\u91cd\u65b0\u6d88\u8d39\uff08{@code nack}/{@code reject}/{@code recover} \u4e14 {@code requeue=true} \u65f6\u7f6e\u4f4d\uff09\u3002
     * @return \u9700\u8981\u91cd\u65b0\u6d88\u8d39\u8fd4\u56de {@code true}
     */
    public boolean shouldReconsume() {
        return reconsume;
    }

    /**
     * \u539f\u5b50\u5360\u7528\u786e\u8ba4\u673a\u4f1a\u5e76\u4fdd\u5b58\u5f53\u524d\u6d88\u606f\u662f\u5426\u9700\u8981\u518d\u6b21\u6d88\u8d39\u3002
     * @param reconsume \u5f53\u524d\u6d88\u606f\u662f\u5426\u9700\u8981\u7531 RocketMQ \u518d\u6b21\u6295\u9012
     */
    private void complete(boolean reconsume) {
        if (!acknowledged.compareAndSet(false, true)) {
            throw new UnsupportedOperationException("Message already acknowledged, msgId=" + message.getMsgId());
        }
        this.reconsume = reconsume;
    }
}
