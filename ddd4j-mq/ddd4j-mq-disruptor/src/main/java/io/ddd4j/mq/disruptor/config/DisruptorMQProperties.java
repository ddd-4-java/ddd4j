package io.ddd4j.mq.disruptor.config;

import lombok.Data;

/**
 * LMAX Disruptor 本地 MQ 配置（前缀 {@code ddd4j.mq.disruptor}）。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class DisruptorMQProperties {

    /** RingBuffer 大小（须为 2 的幂）。 */
    private int bufferSize = 1024;

    /** 等待策略：blocking / yielding / busyspin */
    private String waitStrategy = "yielding";

    /** 消费者线程名前缀 */
    private String threadNamePrefix = "ddd4j-disruptor-";
}
