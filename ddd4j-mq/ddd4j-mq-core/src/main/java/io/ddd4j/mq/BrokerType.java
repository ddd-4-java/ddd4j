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

    NONE,
    /**
     * 进程内 LMAX Disruptor 本地队列（非分布式 MQ）。
     */
    DISRUPTOR,
    RABBIT,
    KAFKA,
    ROCKET,
    PULSAR,
    REDIS_STREAM,
    ACTIVEMQ,
    NATS,
    /**
     * Eclipse Paho MQTT 客户端（连接外部 Broker，非嵌入式服务端）。
     */
    MQTT,
    /**
     * mica-mqtt AIO 客户端（sample mqtt-client2，连接外部 Broker）。
     */
    MQTT_MICA,
    ONS,
    TDMQ,
    SQS;

    /**
     * 解析配置字符串为 Broker 类型（兼容 redisStream 等历史命名）。
     */
    public static BrokerType from(String raw) {
        if (!StrKit.isNotEmpty(raw) || "none".equalsIgnoreCase(raw.trim())) {
            return NONE;
        }
        String normalized = raw.trim()
                .replace('_', '-')
                .toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "disruptor":
            case "local":
            case "local-disruptor":
                return DISRUPTOR;
            case "rabbit":
                return RABBIT;
            case "kafka":
                return KAFKA;
            case "rocket":
                return ROCKET;
            case "pulsar":
                return PULSAR;
            case "redis":
            case "redis-stream":
            case "redisstream":
                return REDIS_STREAM;
            case "activemq":
            case "artemis":
                return ACTIVEMQ;
            case "nats":
                return NATS;
            case "mqtt":
                return MQTT;
            case "mqtt-mica":
            case "mica-mqtt":
            case "mica":
                return MQTT_MICA;
            case "ons":
                return ONS;
            case "tdmq":
                return TDMQ;
            case "sqs":
                return SQS;
            default:
                return NONE;
        }
    }


    /**
     * 转为 kebab-case 配置值（如 {@code redis-stream}）。
     */
    public String toConfigValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
