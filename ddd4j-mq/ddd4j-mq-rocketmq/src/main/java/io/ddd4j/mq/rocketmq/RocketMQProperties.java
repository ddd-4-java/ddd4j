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
package io.ddd4j.mq.rocketmq;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.MQProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.producer.DefaultMQProducer;

/**
 * RocketMQ adapter configuration.
 *
 * <p>{@link RocketMQProperties} extends {@link MQProperties} —— 复用通用字段（namespace / defaultTopic /
 * autoAck / persist / retries / username / password / producerGroup 等），仅声明 RocketMQ 专属字段。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RocketMQProperties extends MQProperties {

    /**
     * NameServer 地址（例：{@code localhost:9876} 或 {@code 192.168.1.1:9876;192.168.1.2:9876}）。
     */
    private String nameServer = "localhost:9876";
    /**
     * 消费者组名前缀（每个 listener 的 group 回落到 {@code consumerGroupPrefix + method}）。
     */
    private String consumerGroupPrefix = "ddd4j";
    /**
     * 生产者是否自动启动。
     */
    private boolean autoStartProducer = true;
    /**
     * 消费者是否自动启动。
     */
    private boolean autoStartConsumers = true;
    /**
     * 发送消息超时（ms）。publish 会阻塞至 broker ack 或此超时后抛
     * {@code RemotingTooMuchRequestException} 让上层 Outbox 重试，避免 broker 卡顿时无限阻塞。
     * 默认 10s 与 RocketMQ 客户端默认一致。
     */
    private int sendMsgTimeoutMillis = 10_000;

    /**
     * 基于本配置创建原生生产者（含 nameServer）。
     */
    public DefaultMQProducer newProducer() {
        DefaultMQProducer producer = new DefaultMQProducer(getProducerGroup());
        if (StrKit.isNotEmpty(nameServer)) {
            producer.setNamesrvAddr(nameServer);
        }
        producer.setSendMsgTimeout(sendMsgTimeoutMillis);
        return producer;
    }

    /**
     * 基于本配置创建原生消费者（含 nameServer）。
     */
    public DefaultMQPushConsumer newConsumer(String group) {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(group);
        if (StrKit.isNotEmpty(nameServer)) {
            consumer.setNamesrvAddr(nameServer);
        }
        return consumer;
    }
}
