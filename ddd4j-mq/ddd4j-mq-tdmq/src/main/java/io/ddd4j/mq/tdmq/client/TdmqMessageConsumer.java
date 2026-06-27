package io.ddd4j.mq.tdmq.client;

import java.util.function.Consumer;

/**
 * TDMQ 消费回调（占位契约，待官方 SDK 接入后替换）。
 */
@FunctionalInterface
public interface TdmqMessageConsumer {

    /**
     * 收到消息时回调。
     *
     * @param messageId     消息 ID
     * @param correlationId 关联 ID
     * @param payload       消息体
     * @param ackCallback   确认回调（true=ack, false=nack/requeue）
     */
    void onMessage(String messageId, String correlationId, byte[] payload, Consumer<Boolean> ackCallback);
}
