package io.ddd4j.mq.delivery;

import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Outbox 可靠投递协调器。
 *
 * <p>领取和状态变更由 {@link MQOutboxStore} 在短事务中实现；本类在事务外调用 {@link MQOutboxSender}，
 * 因而 broker 延迟不会长期占用业务数据库锁。发送成功但条件确认失败时保留至少一次语义。
 */
@Slf4j
public final class MQOutboxDispatcher {

    private final MQOutboxStore store;
    private final MQOutboxSender sender;
    private final MQDeliveryPolicy policy;

    public MQOutboxDispatcher(MQOutboxStore store, MQOutboxSender sender, MQDeliveryPolicy policy) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.sender = Objects.requireNonNull(sender, "sender must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    /**
     * 调度一批当前可投递消息。
     *
     * @param leaseOwner 当前发布实例标识
     * @param limit 本轮最多处理数量
     * @param now 当前时间
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
                } else {
                    confirmationLost++;
                    log.warn("Outbox message {} was sent but its lease confirmation was lost", record.messageId());
                }
            } catch (RuntimeException exception) {
                if (store.reschedule(record.messageId(), leaseOwner, now, exception.getMessage(), policy)) {
                    if (policy.exhausted(record.attempts())) {
                        dead++;
                    } else {
                        rescheduled++;
                    }
                }
                log.warn("Outbox message {} delivery failed", record.messageId(), exception);
            }
        }
        return new MQOutboxDispatchResult(claimed.size(), published, rescheduled, dead, confirmationLost);
    }
}
