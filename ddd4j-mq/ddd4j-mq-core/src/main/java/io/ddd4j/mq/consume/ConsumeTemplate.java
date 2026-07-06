package io.ddd4j.mq.consume;

import io.ddd4j.mq.consume.ack.AckType;
import io.ddd4j.mq.consume.ack.Acknowledgment;
import io.ddd4j.mq.message.Message;
import io.ddd4j.mq.config.BrokerType;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 消费模板（纯 Java，零 Spring 依赖）。
 *
 * <p>统一 preCheck 与 {@link AckType} 到 {@link Acknowledgment} 的映射。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class ConsumeTemplate {

    /**
     * preCheck 返回值：继续执行业务
     */
    public static final int PRE_CONTINUE = 0;

    /**
     * preCheck 返回值：幂等跳过，直接 DISCARD ack
     */
    public static final int PRE_DISCARD = 1;

    /**
     * preCheck 返回值：处理中/锁占用，直接 requeue
     */
    public static final int PRE_DEFER = 2;

    private ConsumeTemplate() {
    }

    /**
     * 执行消费模板：preCheck → business → ack 映射。
     *
     * @param message  消息信封（{@link Message}，纯 Java 模型）
     * @param ack      确认端口
     * @param preCheck 前置检查：0=继续, 1=DISCARD, 2=DEFER
     * @param business 业务逻辑，返回 {@link AckType}
     */
    public static void execute(
            Message<?> message,
            Acknowledgment ack,
            IntSupplier preCheck,
            Supplier<AckType> business) {

        int pre = preCheck.getAsInt();
        if (pre == PRE_DISCARD) {
            ack.ackSingle();
            return;
        }
        if (pre == PRE_DEFER) {
            ack.requeue();
            return;
        }
        applyDisposition(ack, business.get());
    }

    /**
     * 将 {@link AckType} 映射为底层 ack 操作。
     */
    public static void applyDisposition(Acknowledgment ack, AckType disposition) {
        if (Objects.isNull(disposition)) {
            ack.requeue();
            return;
        }
        switch (disposition) {
            case ACK, DISCARD -> ack.ackSingle();
            case REQUEUE -> ack.requeue();
            case REJECT_TO_DLQ -> ack.discard();
            case DEFER -> ack.requeue();
        }
    }

    /**
     * 测试/无 Broker 场景下的空实现确认器，记录最后一次操作类型。
     */
    public static final class RecordingAcknowledgment implements Acknowledgment {

        private final AtomicBoolean acknowledged = new AtomicBoolean(false);
        private volatile String lastOperation;

        @Override
        public long deliveryTag() {
            return 1L;
        }

        @Override
        public String messageId() {
            return "test-msg";
        }

        @Override
        public String correlationId() {
            return null;
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
        public BrokerType brokerType() {
            return BrokerType.NONE;
        }

        @Override
        public void ack() {
            record("ack");
        }

        @Override
        public void ack(boolean multiple) {
            record(multiple ? "ackMultiple" : "ackSingle");
        }

        @Override
        public void nack(boolean requeue) {
            record(requeue ? "nackRequeue" : "nackDiscard");
        }

        @Override
        public void nack(boolean multiple, boolean requeue) {
            record(requeue ? "nackMultipleRequeue" : "nackMultipleDiscard");
        }

        @Override
        public void reject(boolean requeue) {
            record(requeue ? "rejectRequeue" : "rejectDiscard");
        }

        @Override
        public void recover(boolean requeue) {
            record(requeue ? "recoverRequeue" : "recoverDiscard");
        }

        @Override
        public <T> Optional<T> unwrap(Class<T> nativeType) {
            return Optional.empty();
        }

        public String lastOperation() {
            return lastOperation;
        }

        private void record(String operation) {
            if (acknowledged.compareAndSet(false, true)) {
                lastOperation = operation;
            }
        }
    }
}
