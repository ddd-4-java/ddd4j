package io.ddd4j.mq.tdmq.client;

/**
 * TDMQ 订阅句柄，用于取消订阅。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface TdmqSubscription extends AutoCloseable {

    /**
     * 取消订阅。
     */
    @Override
    void close();
}
