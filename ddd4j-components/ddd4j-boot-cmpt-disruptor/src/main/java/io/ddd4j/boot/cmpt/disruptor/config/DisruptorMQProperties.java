package io.ddd4j.boot.cmpt.disruptor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LMAX Disruptor 本地 MQ 配置（前缀 {@code ddd4j.mq.disruptor}）。
 */
@Data
@ConfigurationProperties(prefix = "ddd4j.mq.disruptor")
public class DisruptorMQProperties {

    /** RingBuffer 大小（须为 2 的幂）。 */
    private int bufferSize = 1024;

    /** 等待策略：blocking / yielding / busyspin */
    private String waitStrategy = "yielding";

    /** 消费者线程名前缀 */
    private String threadNamePrefix = "ddd4j-disruptor-";
}
