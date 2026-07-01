package io.ddd4j.mq.tdmq.client;

/**
 * TDMQ 订阅句柄。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface TdmqSubscription extends AutoCloseable {

    @Override
    void close();
}
