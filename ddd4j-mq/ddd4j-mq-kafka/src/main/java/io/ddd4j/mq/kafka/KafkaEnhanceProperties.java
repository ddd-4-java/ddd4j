package io.ddd4j.mq.kafka;

import lombok.Data;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;

@Data
public class KafkaEnhanceProperties {

    private final EnhanceListener listener = new EnhanceListener();

    public enum BackOffMode {

        /**
         * 指数重试
         */
        Exponential,

        /**
         * 固定重试：使用固定的时间间隔重试。
         */
        Fixed

    }

    /**
     * The Error Handler behavior enumeration.
     */
    public enum ErrorHandlerMode {

        /**
         * 使用 {@link org.springframework.kafka.listener.LoggingErrorHandler} 处理错误。
         * 1、记录错误：LoggingErrorHandler 会将错误记录到日志中。
         */
        LOGGING,

        /**
         * 使用 {@link org.springframework.kafka.listener.SeekToCurrentErrorHandler} 处理错误。
         * 1、重置偏移量：当消息处理失败时，SeekToCurrentErrorHandler 会将消费者的偏移量重置到当前拉取批次的开头，重新消费失败的消息。
         * 2、重试机制：可以配置重试次数和重试间隔，避免无限重试。
         */
        SEEK_TO_CURRENT,

        /**
         * 使用 {@link org.springframework.kafka.listener.SeekToCurrentErrorHandler} 和 {@link org.springframework.kafka.listener.DeadLetterPublishingRecoverer} 处理错误。
         * 1、重置偏移量：当消息处理失败时，SeekToCurrentErrorHandler 会将消费者的偏移量重置到当前拉取批次的开头，重新消费失败的消息。
         * 2、重试机制：可以配置重试次数和重试间隔，避免无限重试。
         * 3、死信队列（DLQ）支持：如果消息经过多次重试后仍然失败，可以将消息发送到死信队列（Dead Letter Queue, DLQ）。
         */
        SEEK_TO_CURRENT_WITH_DEAD_LETTER_QUEUE,

    }

    /**
     * The Error Handler behavior enumeration.
     */
    public enum BatchErrorHandlerMode {

        /**
         * 使用 {@link org.springframework.kafka.listener.BatchLoggingErrorHandler} 处理错误。
         * 1、记录错误：BatchLoggingErrorHandler 会将错误记录到日志中。
         */
        LOGGING,

        /**
         * 使用 {@link org.springframework.kafka.listener.SeekToCurrentBatchErrorHandler} 处理错误。
         * 1、重置偏移量：当消息处理失败时，SeekToCurrentErrorHandler 会将消费者的偏移量重置到当前拉取批次的开头，重新消费失败的消息。
         * 2、重试机制：可以配置重试次数和重试间隔，避免无限重试。
         */
        SEEK_TO_CURRENT,

        /**
         * 使用 {@link org.springframework.kafka.listener.SeekToCurrentBatchErrorHandler} 和 {@link org.springframework.kafka.listener.DeadLetterPublishingRecoverer} 处理错误。
         * 1、重置偏移量：当消息处理失败时，SeekToCurrentErrorHandler 会将消费者的偏移量重置到当前拉取批次的开头，重新消费失败的消息。
         * 2、重试机制：可以配置重试次数和重试间隔，避免无限重试。
         * 3、死信队列（DLQ）支持：如果消息经过多次重试后仍然失败，可以将消息发送到死信队列（Dead Letter Queue, DLQ）。
         */
        SEEK_TO_CURRENT_WITH_DEAD_LETTER_QUEUE,

    }

    @Data
    public static class EnhanceListener {

        // 错误处理模式
        private ErrorHandlerMode errorHandlerMode;
        // 批量错误处理模式
        private BatchErrorHandlerMode batchErrorHandlerMode;
        // 重试模式
        private BackOffMode backOffMode;
        /**
         * Whether the offset for a recovered record should be committed.
         *
         * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
         */
        private boolean commitRecovered;
        // 是否在处理消息后立即提交偏移量
        private boolean ackAfterHandle = true;
        // 重试初始间隔
        private long backOffInitialInterval = ExponentialBackOff.DEFAULT_INITIAL_INTERVAL;
        // 重试乘数
        private double backOffMultiplier = ExponentialBackOff.DEFAULT_MULTIPLIER;
        // 重试最大间隔
        private long backOffMaxInterval = ExponentialBackOff.DEFAULT_MAX_INTERVAL;
        // 重试最大时间
        private long backOffMaxElapsedTime = ExponentialBackOff.DEFAULT_MAX_ELAPSED_TIME;
        // 固定重试间隔
        private long backOffInterval = FixedBackOff.DEFAULT_INTERVAL;
        // 重试最大次数
        private long backOffMaxAttempts = 3;

    }

}
