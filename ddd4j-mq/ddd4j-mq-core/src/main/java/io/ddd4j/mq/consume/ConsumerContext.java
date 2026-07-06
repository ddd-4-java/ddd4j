package io.ddd4j.mq.consume;

import io.ddd4j.mq.consume.Acknowledgment;
import io.ddd4j.mq.message.Destination;
import io.ddd4j.mq.message.Message;
import io.ddd4j.mq.message.MessageHeaders;
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
public class ConsumerContext {

    /**
     * 租户 ID（来自 ThreadContext 或消息头）
     */
    private String tenantId;

    /**
     * 消息确认端口
     */
    private Acknowledgment acknowledgment;

    /**
     * 完整消息信封（纯 Java {@link Message}）
     */
    private Message<?> message;

    /**
     * 已按监听器方法签名反序列化后的业务载荷。
     */
    private Object payload;

    /**
     * 消费目的地语义
     */
    private Destination destination;

    /**
     * 获取确认端口别名（与 README 示例 {@code ctx.ack()} 语义对齐的便捷访问）。
     */
    public Acknowledgment ack() {
        return acknowledgment;
    }

    /**
     * 读取 header。
     */
    public Object header(String key) {
        return MessageHeaders.header(message, key);
    }

    /**
     * 返回不可变的 headers 视图。
     */
    public Map<String, Object> getHeaders() {
        return MessageHeaders.headers(message);
    }
}
