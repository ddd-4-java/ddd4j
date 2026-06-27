package io.ddd4j.mq.ack;

import io.ddd4j.mq.registry.MQBrokerType;
import lombok.Builder;
import lombok.Data;

/**
 * 确认上下文：Adapter 解析 {@link MessageAcknowledgment} 时携带的元数据与原生句柄。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@Builder
public class AcknowledgmentContext {

    /** 投递标签 */
    private long deliveryTag;

    /** 消息 ID */
    private String messageId;

    /** 关联 ID */
    private String correlationId;

    /** Broker 类型 */
    @Builder.Default
    private MQBrokerType brokerType = MQBrokerType.NONE;

    /** 通道/连接是否打开 */
    @Builder.Default
    private boolean open = true;

    /** 是否已确认 */
    @Builder.Default
    private boolean acknowledged = false;

    /** Broker 原生句柄（Channel、Acknowledgment 等），供 unwrap 使用 */
    private Object nativeHandle;
}
