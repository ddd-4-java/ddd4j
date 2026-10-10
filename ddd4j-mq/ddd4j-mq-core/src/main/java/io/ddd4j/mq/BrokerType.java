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
package io.ddd4j.mq;

import io.ddd4j.kit.lang.StrKit;

import java.util.Locale;

/**
 * 支持的 Broker 类型枚举（与 {@code ddd4j.mq.broker} 对齐）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public enum BrokerType {

    /** 未指定消息代理。 */
    NONE,
    /**
     * 进程内 LMAX Disruptor 本地队列（非分布式 MQ）。
     */
    DISRUPTOR,
    /** 基于 AMQP 协议的消息代理。 */
    RABBIT,
    /** 分布式分区日志型消息队列。 */
    KAFKA,
    /** 分布式消息队列，支持事务消息与重试。 */
    ROCKET,
    /** 分布式流式消息队列，支持多租户。 */
    PULSAR,
    /** 基于内存键值流结构的队列。 */
    REDIS_STREAM,
    /** 基于 JMS 规范的消息代理。 */
    ACTIVEMQ,
    /** 轻量级发布订阅消息代理。 */
    NATS,
    /**
     * Eclipse Paho MQTT 客户端（连接外部 Broker，非嵌入式服务端）。
     */
    MQTT,
    /**
     * mica-mqtt AIO 客户端（sample mqtt-client2，连接外部 Broker）。
     */
    MQTT_MICA,
    /** ONS 类型消息队列。 */
    ONS,
    /** TDMQ 类型消息队列。 */
    TDMQ,
    /** SQS 类型消息队列服务。 */
    SQS;

    /**
     * 解析配置字符串为 Broker 类型（兼容 redisStream 等历史命名）。
     */
    public static BrokerType from(String raw) {
        if (!StrKit.hasText(raw) || "none".equalsIgnoreCase(raw.trim())) {
            return NONE;
        }
        String normalized = raw.trim()
                .replace('_', '-')
                .toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "disruptor", "local", "local-disruptor" -> DISRUPTOR;
            case "rabbit" -> RABBIT;
            case "kafka" -> KAFKA;
            case "rocket" -> ROCKET;
            case "pulsar" -> PULSAR;
            case "redis", "redis-stream", "redisstream" -> REDIS_STREAM;
            case "activemq", "artemis" -> ACTIVEMQ;
            case "nats" -> NATS;
            case "mqtt" -> MQTT;
            case "mqtt-mica", "mica-mqtt", "mica" -> MQTT_MICA;
            case "ons" -> ONS;
            case "tdmq" -> TDMQ;
            case "sqs" -> SQS;
            default -> NONE;
        };
    }


    /**
     * 转为 kebab-case 配置值（如 {@code redis-stream}）。
     */
    public String toConfigValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
