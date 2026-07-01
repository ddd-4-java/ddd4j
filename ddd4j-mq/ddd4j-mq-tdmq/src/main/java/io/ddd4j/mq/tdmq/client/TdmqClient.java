package io.ddd4j.mq.tdmq.client;

/**
 * 腾讯云 TDMQ 客户端契约。
 *
 * <p>当前模块暴露纯 Java SPI；业务可用官方 SDK 包装实现替换默认占位客户端。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface TdmqClient {

    boolean isReady();

    void publish(String topic, String tag, byte[] payload);

    TdmqSubscription subscribe(String topic, String tagExpression, String group, TdmqMessageConsumer consumer);
}
