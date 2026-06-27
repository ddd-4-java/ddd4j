package io.ddd4j.mq.tdmq.client;

/**
 * 腾讯云 TDMQ 客户端占位接口（待接入官方 tdmq-client / Pulsar 兼容 SDK）。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface TdmqClient {

    /**
     * 客户端是否已就绪。
     *
     * @return 就绪时 true
     */
    boolean isReady();

    /**
     * 发布消息到指定 topic。
     *
     * @param topic   主题
     * @param tag     标签
     * @param payload 消息体
     */
    void publish(String topic, String tag, byte[] payload);

    /**
     * 订阅 topic（占位实现使用进程内总线模拟）。
     *
     * @param topic    主题
     * @param tag      标签（可为 null 表示全部）
     * @param group    消费组
     * @param consumer 消费回调
     * @return 订阅句柄
     */
    TdmqSubscription subscribe(String topic, String tag, String group, TdmqMessageConsumer consumer);
}
