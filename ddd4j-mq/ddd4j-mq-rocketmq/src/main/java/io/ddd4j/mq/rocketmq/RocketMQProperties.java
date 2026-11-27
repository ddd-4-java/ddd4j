package io.ddd4j.mq.rocketmq;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.MQProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.producer.DefaultMQProducer;

/**
 * RocketMQ adapter configuration.
 *
 * <p>{@link RocketMQProperties} extends {@link MQProperties} —— 复用通用字段（namespace / defaultTopic /
 * autoAck / persist / retries / username / password / producerGroup 等），仅声明 RocketMQ 专属字段。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RocketMQProperties extends MQProperties {

    /**
     * NameServer 地址（例：{@code localhost:9876} 或 {@code 192.168.1.1:9876;192.168.1.2:9876}）。
     */
    private String nameServer = "localhost:9876";
    /**
     * 消费者组名前缀（每个 listener 的 group 回落到 {@code consumerGroupPrefix + method}）。
     */
    private String consumerGroupPrefix = "ddd4j";
    /**
     * 生产者是否自动启动。
     */
    private boolean autoStartProducer = true;
    /**
     * 消费者是否自动启动。
     */
    private boolean autoStartConsumers = true;

    /**
     * \u57fa\u4e8e\u672c\u914d\u7f6e\u521b\u5efa\u539f\u751f\u751f\u4ea7\u8005\uff08\u542b nameServer\uff09\u3002
     *
     * @return \u5df2\u5e94\u7528 nameServer / \u53d1\u9001\u8d85\u65f6 / API \u8d85\u65f6\u7684\u539f\u751f\u751f\u4ea7\u8005\uff08\u672a start\uff09
     */
    public DefaultMQProducer newProducer() {
        DefaultMQProducer producer = new DefaultMQProducer(getProducerGroup());
        if (StrKit.isNotEmpty(nameServer)) {
            producer.setNamesrvAddr(nameServer);
        }
        return producer;
    }

    /**
     * \u57fa\u4e8e\u672c\u914d\u7f6e\u521b\u5efa\u539f\u751f\u6d88\u8d39\u8005\uff08\u542b nameServer\uff09\u3002
     *
     * @param group \u6d88\u8d39\u8005\u7ec4\u540d
     * @return \u5df2\u5e94\u7528 nameServer / API \u8d85\u65f6\u7684\u539f\u751f\u6d88\u8d39\u8005\uff08\u672a start\uff09
     */
    public DefaultMQPushConsumer newConsumer(String group) {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(group);
        if (StrKit.isNotEmpty(nameServer)) {
            consumer.setNamesrvAddr(nameServer);
        }
        return consumer;
    }
}
