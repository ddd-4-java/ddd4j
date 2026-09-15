/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.mq.rabbitmq;

import com.rabbitmq.client.ConnectionFactory;
import io.ddd4j.mq.MQProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * RabbitMQ adapter configuration.
 *
 * <p>{@link RabbitMQProperties} extends {@link MQProperties} —— 复用通用字段（namespace / defaultTopic /
 * autoAck / persist / retries / username / password / exchange 等），仅声明 RabbitMQ 专属字段。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RabbitMQProperties extends MQProperties {

    /**
     * Broker 主机
     */
    private String host = "localhost";
    /**
     * Broker 端口
     */
    private int port = 5672;
    /**
     * 虚拟主机
     */
    private String virtualHost = "/";
    /**
     * 队列/绑定是否持久化
     */
    private boolean durable = true;
    /**
     * 是否在注册时自动声明 queue / binding
     */
    private boolean autoDeclare = true;
    /** 发布后是否等待 broker confirm。 */
    private boolean publisherConfirmRequired = true;
    /** broker confirm 最长等待时间。 */
    private long publisherConfirmTimeoutMillis = 5000L;
    /**
     * 生产者 channel 池大小上限。Channel 非线程安全，因此 ddd4j-mq 用 {@code ThreadLocal} 让
     * 每个发布线程独占一个 channel。在 Java 21+ 虚拟线程场景下，每个虚拟线程都会创建独立
     * channel，可能触发 broker 连接数 / fd 上限。本字段将上限锁定为固定值：
     * 超出后虚拟线程回退为阻塞等待池中已有 channel（仍保持线程安全，吞吐降级而非崩溃）。
     * 默认 32 足够覆盖常规 HTTP/Web 工作负载。
     */
    private int producerChannelPoolSize = 32;

    /**
     * 基于本配置（含父类 username/password）创建原生 {@link ConnectionFactory}。
     */
    public ConnectionFactory connectionFactory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(getUsername());
        factory.setPassword(getPassword());
        factory.setVirtualHost(virtualHost);
        return factory;
    }
}
