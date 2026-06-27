/**
 * ddd4j-mq-spring：Spring Messaging 与 ddd4j-mq-core 之间的桥接。
 * <p>
 * 本模块依赖 org.springframework.messaging，但不依赖具体 broker SDK。
 * 各 broker 实现（kafka/rabbitmq/rocketmq 等）通过本模块完成 Spring 集成。
 *
 * @since 3.4.x
 */
package io.ddd4j.mq.spring;
