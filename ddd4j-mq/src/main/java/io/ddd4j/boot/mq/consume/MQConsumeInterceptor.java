package io.ddd4j.boot.mq.consume;

import io.ddd4j.boot.mq.acknowledgment.AckDisposition;
import io.ddd4j.boot.mq.contract.MQMessage;

/**
 * 消费拦截链：幂等检查、消息流水等横切能力挂载点。
 * <p>
 * preCheck 返回值与 {@link io.ddd4j.boot.mq.acknowledgment.MQConsumeTemplates} 一致：
 * 0=继续, 1=DISCARD, 2=DEFER。
 */
public interface MQConsumeInterceptor {

    /**
     * 拦截器顺序，值越小越先执行。
     *
     * @return 顺序值
     */
    default int order() {
        return 0;
    }

    /**
     * 消费前检查。
     *
     * @param context 消费上下文
     * @param message 消息信封
     * @return 0=继续, 1=DISCARD, 2=DEFER
     */
    default int preCheck(MQConsumerContext context, MQMessage<?> message) {
        return 0;
    }

    /**
     * 消费完成后回调（无论成功或失败，在 ack 映射之后）。
     *
     * @param context     消费上下文
     * @param message     消息信封
     * @param disposition 最终业务处置结果，preCheck 短路时可能为 null
     */
    default void afterConsume(MQConsumerContext context, MQMessage<?> message, AckDisposition disposition) {
        // 默认无操作
    }
}
