package io.ddd4j.mq.consume;

import io.ddd4j.mq.message.Message;

/**
 * 原生消息转换器（各 Broker 最小实现接口）。
 *
 * <p>各 Broker Adapter 只需实现这个接口，把 Broker 原生消息（如 Kafka ConsumerRecord、
 * Rabbit Message、NATS Message、Rocket MessageExt 等）转成纯 Java {@link Message}。
 *
 * <p>配合 {@link io.ddd4j.mq.consume.Acknowledgment} 的 Factory 模式，
 * Broker 只需提供这两个转换函数，不再自己写 onMessage 全流程。
 *
 * @param <N> Broker 原生消息类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@FunctionalInterface
public interface MessageConverter<N> {

    /**
     * 把 Broker 原生消息转成纯 Java Message。
     *
     * @param nativeMessage Broker 原生消息（如 Kafka ConsumerRecord / Rabbit Message / NATS Message）
     * @return 纯 Java 消息信封
     */
    Message<?> convert(N nativeMessage);
}
