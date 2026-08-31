package io.ddd4j.mq.disruptor.core;

import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

/**
 * Disruptor RingBuffer 事件槽：承载本地 MQ 消息载荷与路由元数据。
 */
@Getter
@Setter
public class DisruptorMQEvent {

    private String topic;
    private String tag;
    private String namespace;
    private String messageId;
    private String correlationId;
    private String payload;
    private long sequence;
    private boolean requeue;

    /**
     * 生成路由键：namespace.topic[.tag]。
     */
    public String routeKey() {
        String base = namespace + "." + topic;
        if (!StringUtils.hasText(tag) || "*".equals(tag)) {
            return base;
        }
        return base + "." + tag;
    }

    /**
     * 从发布参数填充事件槽。
     */
    public void copyFrom(String namespace, String topic, String tag, String messageId,
                         String correlationId, String payload, long sequence) {
        this.namespace = namespace;
        this.topic = topic;
        this.tag = tag;
        this.messageId = messageId;
        this.correlationId = correlationId;
        this.payload = payload;
        this.sequence = sequence;
        this.requeue = false;
    }

    /**
     * 清空事件槽（消费完成后复用）。
     */
    public void clear() {
        this.topic = null;
        this.tag = null;
        this.namespace = null;
        this.messageId = null;
        this.correlationId = null;
        this.payload = null;
        this.sequence = 0L;
        this.requeue = false;
    }
}
