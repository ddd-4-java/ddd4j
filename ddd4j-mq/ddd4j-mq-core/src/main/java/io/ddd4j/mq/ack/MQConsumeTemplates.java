package io.ddd4j.mq.ack;

import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.registry.MQBrokerType;
import org.springframework.messaging.Message;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 消费模板：统一 preCheck 与 {@link AckDisposition} 到 {@link MessageAcknowledgment} 的映射。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class MQConsumeTemplates {

    /** preCheck 返回值：继续执行业务 */
    public static final int PRE_CONTINUE = 0;

    /** preCheck 返回值：幂等跳过，直接 DISCARD ack */
    public static final int PRE_DISCARD = 1;

    /** preCheck 返回值：处理中/锁占用，直接 requeue */
    public static final int PRE_DEFER = 2;

    private MQConsumeTemplates() {
    }

    /**
     * 执行消费模板：preCheck → business → ack 映射。
     *
     * @param message  消息信封（{@link Message}，当前模板不直接使用，保留扩展点）
     * @param ack      确认端口
     * @param preCheck 前置检查：0=继续, 1=DISCARD, 2=DEFER
     * @param business 业务逻辑，返回 {@link AckDisposition}
     */
    public static void execute(
            Message<?> message,
            MessageAcknowledgment ack,
            IntSupplier preCheck,
            Supplier<AckDisposition> business) {

        // 前置检查：幂等跳过或 defer 时短路，不进入业务
        int pre = preCheck.getAsInt();
        if (pre == PRE_DISCARD) {
            ack.ackSingle();
            return;
        }
        if (pre == PRE_DEFER) {
            ack.requeue();
            return;
        }

        // 业务结果 → Broker 确认语义
        applyDisposition(ack, business.get());
    }

    /**
     * 执行消费模板（兼容旧 {@link MQMessage} 参数）。
     *
     * @param message  消息信封（{@link MQMessage}，兼容旧 API）
     * @param ack      确认端口
     * @param preCheck 前置检查：0=继续, 1=DISCARD, 2=DEFER
     * @param business 业务逻辑，返回 {@link AckDisposition}
     */
    public static void execute(
            MQMessage<?> message,
            MessageAcknowledgment ack,
            IntSupplier preCheck,
            Supplier<AckDisposition> business) {
        execute(message.toMessage(), ack, preCheck, business);
    }

    /**
     * 将 {@link AckDisposition} 映射为底层 ack 操作。
     *
     * @param ack         确认端口
     * @param disposition 业务处置结果
     */
    public static void applyDisposition(MessageAcknowledgment ack, AckDisposition disposition) {
        if (disposition == null) {
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
    public static final class RecordingAcknowledgment implements MessageAcknowledgment {

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
        public MQBrokerType brokerType() {
            return MQBrokerType.NONE;
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

        /**
         * @return 最后一次确认操作名
         */
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
