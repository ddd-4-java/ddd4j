package io.ddd4j.mq.consume;

import io.ddd4j.mq.acknowledgment.MessageAcknowledgment;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.contract.MQMessage;
import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.Map;

/**
 * 消费上下文：租户、确认端口、原始 headers 及消息信封。
 */
@Data
@Builder
public class MQConsumerContext {

    /** 租户 ID（来自 ThreadContext 或消息头） */
    private String tenantId;

    /** 消息确认端口 */
    private MessageAcknowledgment acknowledgment;

    /** 原始消息头（不可变视图） */
    @Builder.Default
    private Map<String, Object> headers = Map.of();

    /** 完整消息信封 */
    private MQMessage<?> message;

    /** 消费目的地语义 */
    private MQDestination destination;

    /**
     * 获取确认端口别名（与 README 示例 {@code ctx.ack()} 语义对齐的便捷访问）。
     *
     * @return 确认端口，可能为 null
     */
    public MessageAcknowledgment ack() {
        return acknowledgment;
    }

    /**
     * 读取 header。
     *
     * @param key 键
     * @return 值或 null
     */
    public Object header(String key) {
        return headers.get(key);
    }

    /**
     * 返回不可变的 headers 视图。
     *
     * @return 消息头
     */
    public Map<String, Object> getHeaders() {
        return headers == null ? Map.of() : Collections.unmodifiableMap(headers);
    }
}
