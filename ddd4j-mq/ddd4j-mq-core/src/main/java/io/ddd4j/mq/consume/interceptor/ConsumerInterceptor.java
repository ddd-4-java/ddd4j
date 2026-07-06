package io.ddd4j.mq.consume.interceptor;

import io.ddd4j.mq.consume.ConsumerContext;
import io.ddd4j.mq.consume.ack.AckType;
import io.ddd4j.mq.message.Message;

/**
 * 消费拦截链 SPI（纯 Java，零 Spring 依赖）。
 *
 * <p>用于挂载幂等检查、消息流水等横切能力。
 *
 * <p>preCheck 返回值与 {@link io.ddd4j.mq.consume.ConsumeTemplate} 一致：
 * 0=继续, 1=DISCARD, 2=DEFER。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface ConsumerInterceptor {

    /**
     * 拦截器顺序，值越小越先执行。
     */
    default int order() {
        return 0;
    }

    /**
     * 消费前检查。
     *
     * @param context 消费上下文
     * @param message 消息信封（{@link Message}，纯 Java 模型）
     * @return 0=继续, 1=DISCARD, 2=DEFER
     */
    default int preCheck(ConsumerContext context, Message<?> message) {
        return 0;
    }

    /**
     * 消费完成后回调（无论成功或失败，在 ack 映射之后）。
     *
     * @param context     消费上下文
     * @param message     消息信封（{@link Message}）
     * @param disposition 最终业务处置结果，preCheck 短路时可能为 null
     */
    default void afterConsume(ConsumerContext context, Message<?> message, AckType disposition) {
        // 默认无操作
    }
}
