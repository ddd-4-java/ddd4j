package io.ddd4j.boot.cmpt.tdmq.client;

/**
 * TDMQ 订阅句柄，用于取消订阅。
 */
public interface TdmqSubscription extends AutoCloseable {

    /**
     * 取消订阅。
     */
    @Override
    void close();
}
