package io.ddd4j.mq.delivery;

import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Outbox 可靠投递协调器。
 *
 * <p>领取和状态变更由 {@link MQOutboxStore} 在短事务中实现；本类在事务外调用 {@link MQOutboxSender}，
 * 因而 broker 延迟不会长期占用业务数据库锁。发送成功但条件确认失败时保留至少一次语义。
 */
@Slf4j
public final class MQOutboxDispatcher {

    /** \u8d1f\u8d23\u8ba4\u9886\u3001\u786e\u8ba4\u548c\u91cd\u65b0\u5b89\u6392\u5f85\u53d1\u4e8b\u4ef6\u7684\u53d1\u4ef6\u7bb1\u5b58\u50a8\u3002 */
    private final MQOutboxStore store;
    /** \u6267\u884c\u5bf9\u6d88\u606f\u4ee3\u7406\u5b9e\u9645\u53d1\u9001\u7684\u7ec4\u4ef6\u3002 */
    private final MQOutboxSender sender;
    /** \u8ba1\u7b97\u8ba4\u9886\u79df\u7ea6\u53ca\u5931\u8d25\u91cd\u8bd5\u6761\u4ef6\u7684\u7b56\u7565\u3002 */
    private final MQDeliveryPolicy policy;
    /** \u63a5\u6536\u6d88\u606f\u6295\u9012\u6216\u8fd0\u884c\u72b6\u6001\u53d8\u5316\u7684\u56de\u8c03\u3002 */
    private final MQDeliveryObserver observer;

    /**
     * \u521b\u5efa\u53ef\u9760\u6d88\u606f\u8c03\u5ea6\u5668\u3002
     * @param store Outbox \u6301\u4e45\u5316\u7aef\u53e3
     * @param sender \u6d88\u606f\u53d1\u9001\u7aef\u53e3
     * @param policy \u6295\u9012\u7b56\u7565
     */
    public MQOutboxDispatcher(MQOutboxStore store, MQOutboxSender sender, MQDeliveryPolicy policy) {
        this(store, sender, policy, NoopMQDeliveryObserver.INSTANCE);
    }

    /**
     * 创建带投递结果观察器的调度器。
     *
     * @param store    Outbox 持久化端口
     * @param sender   Broker 发送端口
     * @param policy   投递策略
     * @param observer 旁路观测实现
     */
    public MQOutboxDispatcher(MQOutboxStore store, MQOutboxSender sender, MQDeliveryPolicy policy,
                              MQDeliveryObserver observer) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.sender = Objects.requireNonNull(sender, "sender must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.observer = Objects.requireNonNull(observer, "observer must not be null");
    }

    /**
     * 调度一批当前可投递消息。
     *
     * @param leaseOwner 当前发布实例标识
     * @param limit      本轮最多处理数量
     * @param now        当前时间
     * @return 调度结果
     */
    public MQOutboxDispatchResult dispatch(String leaseOwner, int limit, Instant now) {
        if (StrKit.isBlank(leaseOwner)) {
            throw new IllegalArgumentException("leaseOwner must not be blank");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }
        Objects.requireNonNull(now, "now must not be null");
        List<MQOutboxRecord> claimed = store.claim(leaseOwner, now, limit, policy);
        int published = 0;
        int rescheduled = 0;
        int dead = 0;
        int confirmationLost = 0;
        for (MQOutboxRecord record : claimed) {
            try {
                sender.send(record);
                if (store.markPublished(record.messageId(), leaseOwner, now)) {
                    published++;
                    notifyObserver(deliveryObserver -> deliveryObserver.onOutboxPublished(record));
                } else {
                    confirmationLost++;
                    log.warn("Outbox message {} was sent but its lease confirmation was lost", record.messageId());
                    notifyObserver(deliveryObserver -> deliveryObserver.onOutboxFailed(record));
                }
            } catch (RuntimeException exception) {
                try {
                    if (store.reschedule(record.messageId(), leaseOwner, now, exception.getMessage(), policy)) {
                        if (policy.exhausted(record.attempts())) {
                            dead++;
                            notifyObserver(deliveryObserver -> deliveryObserver.onOutboxDead(record));
                        } else {
                            rescheduled++;
                            notifyObserver(deliveryObserver -> deliveryObserver.onOutboxRetry(record));
                        }
                    } else {
                        notifyObserver(deliveryObserver -> deliveryObserver.onOutboxFailed(record));
                    }
                } catch (RuntimeException storeException) {
                    notifyObserver(deliveryObserver -> deliveryObserver.onOutboxFailed(record));
                    log.warn("Outbox message {} delivery state update failed", record.messageId(), storeException);
                    throw storeException;
                }
                log.warn("Outbox message {} delivery failed", record.messageId(), exception);
            }
        }
        return new MQOutboxDispatchResult(claimed.size(), published, rescheduled, dead, confirmationLost);
    }

    /**
     * \u901a\u77e5\u6295\u9012\u89c2\u5bdf\u5668\u5e76\u8bb0\u5f55\u5176\u8fd0\u884c\u65f6\u5f02\u5e38\uff0c\u907f\u514d\u5f71\u54cd\u5b9e\u9645\u6295\u9012\u8def\u5f84\u3002
     * @param action \u8981\u5728\u5f53\u524d\u89c2\u5bdf\u5668\u4e0a\u8c03\u7528\u7684\u901a\u77e5\u52a8\u4f5c
     */
    private void notifyObserver(Consumer<MQDeliveryObserver> action) {
        try {
            action.accept(observer);
        } catch (RuntimeException exception) {
            log.warn("MQ delivery observer failed and was ignored", exception);
        }
    }
}
