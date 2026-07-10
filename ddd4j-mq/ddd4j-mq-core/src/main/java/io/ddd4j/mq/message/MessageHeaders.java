package io.ddd4j.mq.message;

/**
 * 标准 Header Keys（纯 Java，零 Spring 依赖）。
 *
 * <p>各 Broker 的 Publisher / Consumer 通过这些常量 key 读写消息属性，
 * 统一 topic / tag / tenant / correlation 等元数据的传递契约。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public final class MessageHeaders {

    // ── 标准 Header Keys ──

    /**
     * 底层 Broker 原生消息（逃生口，通常不直接放进 header）
     */
    public static final String HEADER_NATIVE_MESSAGE = "ddd4j.native.message";

    /**
     * 消息 ID
     */
    public static final String HEADER_MESSAGE_ID = "ddd4j.message.id";

    /**
     * 关联 ID
     */
    public static final String HEADER_CORRELATION_ID = "ddd4j.correlation.id";

    /**
     * 因果 ID
     */
    public static final String HEADER_CAUSATION_ID = "ddd4j.causation.id";

    /**
     * 租户 ID
     */
    public static final String HEADER_TENANT_ID = "ddd4j.tenant.id";

    /**
     * Broker 类型
     */
    public static final String HEADER_BROKER_TYPE = "ddd4j.broker.type";

    /**
     * 目的地 topic
     */
    public static final String HEADER_DESTINATION_TOPIC = "ddd4j.destination.topic";

    /**
     * 目的地 tag
     */
    public static final String HEADER_DESTINATION_TAG = "ddd4j.destination.tag";

    /**
     * 目的地 namespace
     */
    public static final String HEADER_DESTINATION_NAMESPACE = "ddd4j.destination.namespace";

    private MessageHeaders() {
    }
}
