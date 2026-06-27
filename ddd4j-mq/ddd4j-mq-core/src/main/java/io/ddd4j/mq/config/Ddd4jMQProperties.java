
package io.ddd4j.mq.config;

import io.ddd4j.core.contract.MQEvent.PublishMode;
import io.ddd4j.mq.registry.MQBrokerType;
import lombok.Data;

/**
 * ddd4j MQ 主配置（前缀 {@code ddd4j.mq}）。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
// @ConfigurationProperties(prefix = "ddd4j.mq")
public class Ddd4jMQProperties {

    /** 是否启用 MQ */
    private boolean enabled = false;

    /** 事件发布模式：MQ | SPRING_EVENT | BOTH */
    private PublishMode publishMode = PublishMode.MQ;

    /** 当前 Broker 类型（配置字符串，如 rabbit、kafka） */
    private String broker = "none";

    /** 命名空间 / 环境前缀 */
    private String namespace = "";

    /** {@code MQEvent.publish()} 默认 topic */
    private String defaultTopic = "DEFAULT";

    /** 消费端配置 */
    private Consumer consumer = new Consumer();

    /** 序列化器 Bean 名或类型标识 */
    private String serialization = "json";

    /** 是否启用消息本地持久化（需 {@code MQEventStorer} Bean） */
    private boolean persist = false;

    /** 发送失败重试次数（由 cmpt 实现） */
    private int retries = 0;

    /**
     * 解析 {@link #broker} 为枚举。
     *
     * @return Broker 类型
     */
    public MQBrokerType brokerType() {
        return MQBrokerType.from(broker);
    }

    /**
     * 消费端子配置。
     */
    @Data
    public static class Consumer {

        /** 确认模式：manual / auto */
        private String ackMode = "manual";

        /**
         * 是否为手动确认模式。
         *
         * @return manual 时 true
         */
        public boolean isManualAck() {
            return !"auto".equalsIgnoreCase(ackMode);
        }
    }
}
