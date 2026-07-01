package io.ddd4j.mq.tdmq.client;

import java.util.function.Consumer;

/**
 * TDMQ 消费回调契约。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@FunctionalInterface
public interface TdmqMessageConsumer {

    void onMessage(String messageId, String correlationId, byte[] payload, Consumer<Boolean> ackCallback);
}
