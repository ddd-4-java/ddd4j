package io.ddd4j.mq;

import io.ddd4j.kit.lang.StrKit;

import java.util.Locale;

/**
 * 支持的 Broker 类型枚举（与 {@code ddd4j.mq.broker} 对齐）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public enum BrokerType {

    /** \u672a\u542f\u7528 Broker\u3002 */
    NONE,
    /**
     * 进程内 LMAX Disruptor 本地队列（非分布式 MQ）。
     */
    DISRUPTOR,
    /** RabbitMQ\u3002 */
    RABBIT,
    /** Kafka\u3002 */
    KAFKA,
    /** RocketMQ\u3002 */
    ROCKET,
    /** Pulsar\u3002 */
    PULSAR,
    /** Redis Stream\u3002 */
    REDIS_STREAM,
    /** ActiveMQ\u3002 */
    ACTIVEMQ,
    /** NATS\u3002 */
    NATS,
    /**
     * Eclipse Paho MQTT 客户端（连接外部 Broker，非嵌入式服务端）。
     */
    MQTT,
    /**
     * mica-mqtt AIO 客户端（sample mqtt-client2，连接外部 Broker）。
     */
    MQTT_MICA,
    /** \u963f\u91cc\u4e91 ONS\u3002 */
    ONS,
    /** \u817e\u8baf\u4e91 TDMQ\u3002 */
    TDMQ,
    /** AWS SQS\u3002 */
    SQS;

    /**
     * \u89e3\u6790\u914d\u7f6e\u5b57\u7b26\u4e32\u4e3a Broker \u7c7b\u578b\uff08\u517c\u5bb9 redisStream \u7b49\u5386\u53f2\u547d\u540d\uff09\u3002
     *
     * @param raw \u539f\u59cb\u914d\u7f6e\u503c
     * @return \u5339\u914d\u7684 Broker \u7c7b\u578b\uff1b\u672a\u77e5\u503c\u8fd4\u56de NONE
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
     * \u8f6c\u4e3a kebab-case \u914d\u7f6e\u503c\uff08\u5982 {@code redis-stream}\uff09\u3002
     *
     * @return kebab-case \u914d\u7f6e\u503c
     */
    public String toConfigValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
