package io.ddd4j.sample.spring.mq.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Disruptor 本地 MQ 配置。
 *
 * <p>ddd4j-mq-disruptor 模块使用 LMAX Disruptor 作为进程内 RingBuffer 实现，
 * 完全本地、无任何外部 Broker 依赖，最适合作为 sample 演示。
 *
 * <p>生产环境切换为 Kafka / RabbitMQ / RocketMQ 时：
 * <ul>
 *   <li>移除 pom 中的 {@code ddd4j-mq-disruptor} 依赖</li>
 *   <li>引入对应的 {@code ddd4j-mq-kafka} / {@code ddd4j-mq-rabbitmq} 等</li>
 *   <li>在 application.yml 中配置 broker 连接信息</li>
 *   <li>业务代码（{@code @MQEventListener}、{@code MQEventPublisher}）完全无需修改</li>
 * </ul>
 *
 * <p>ddd4j-mq-disruptor 的自动装配由 {@code ddd4j-mq-spring} 模块提供，
 * 通过 SPI 机制自动将 DisruptorMQEventPublisher 注册到 ddd4j 上下文，
 * 无需额外的 {@code @Bean} 配置。本类仅作为配置说明的占位。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@Configuration
public class DisruptorMqConfig {

    /**
     * ddd4j-mq-spring 模块自动完成以下注册：
     * <ol>
     *   <li>扫描 {@code @MQEventListener} 注解的方法，注册为 MQ 消费者</li>
     *   <li>将 {@code DisruptorMQEventPublisher} 注入 ddd4j SPI 上下文</li>
     *   <li>启动 Disruptor RingBuffer 事件消费循环</li>
     * </ol>
     *
     * <p>业务代码只需使用：
     * <ul>
     *   <li>{@code @MQEventListener(topic, tags)} 声明消费者</li>
     *   <li>{@code MQEvent.publish()} 发布事件</li>
     * </ul>
     */

    /**
     * Disruptor MQ 配置加载（Spring 启动时自动执行）。
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("========== Disruptor MQ 已就绪（本地 RingBuffer，无外部 Broker） ==========");
        log.info("MQ 消费者通过 @MQEventListener 注解自动注册");
    }
}
