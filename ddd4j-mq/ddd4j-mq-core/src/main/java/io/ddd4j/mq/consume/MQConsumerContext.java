package io.ddd4j.mq.consume;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.contract.MQMessages;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 消费上下文（纯 Java，零 Spring 依赖）。
 *
 * <p>聚合租户、确认端口、原生 headers 与消息信封，供消费方法与拦截器使用。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@Builder
public class MQConsumerContext {

    /** 租户 ID（来自 ThreadContext 或消息头） */
    private String tenantId;

    /** 消息确认端口 */
    private MessageAcknowledgment acknowledgment;

    /** 完整消息信封（纯 Java {@link MQMessage}） */
    private MQMessage<?> message;

    /** 消费目的地语义 */
    private MQDestination destination;

    /**
     * 获取确认端口别名（与 README 示例 {@code ctx.ack()} 语义对齐的便捷访问）。
     */
    public MessageAcknowledgment ack() {
        return acknowledgment;
    }

    /**
     * 读取 header。
     */
    public Object header(String key) {
        return MQMessages.header(message, key);
    }

    /**
     * 返回不可变的 headers 视图。
     */
    public Map<String, Object> getHeaders() {
        return MQMessages.headers(message);
    }
}
