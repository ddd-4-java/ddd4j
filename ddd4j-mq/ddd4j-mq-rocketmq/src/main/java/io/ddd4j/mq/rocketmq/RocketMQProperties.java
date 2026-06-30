package io.ddd4j.mq.rocketmq;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.producer.DefaultMQProducer;

/**
 * RocketMQ adapter configuration.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class RocketMQProperties {

    private String nameServer = "localhost:9876";
    private String producerGroup = "ddd4j-producer";
    private String consumerGroupPrefix = "ddd4j";
    private boolean autoStartProducer = true;
    private boolean autoStartConsumers = true;

    private static boolean hasText(String s) {
        return java.util.Objects.nonNull(s) && !io.ddd4j.kit.lang.StrKit.isBlank(s);
    }

    public DefaultMQProducer newProducer() {
        DefaultMQProducer producer = new DefaultMQProducer(producerGroup);
        if (hasText(nameServer)) {
            producer.setNamesrvAddr(nameServer);
        }
        return producer;
    }

    public DefaultMQPushConsumer newConsumer(String group) {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(group);
        if (hasText(nameServer)) {
            consumer.setNamesrvAddr(nameServer);
        }
        return consumer;
    }

    public String getNameServer() {
        return nameServer;
    }

    public void setNameServer(String nameServer) {
        this.nameServer = nameServer;
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public void setProducerGroup(String producerGroup) {
        this.producerGroup = producerGroup;
    }

    public String getConsumerGroupPrefix() {
        return consumerGroupPrefix;
    }

    public void setConsumerGroupPrefix(String consumerGroupPrefix) {
        this.consumerGroupPrefix = consumerGroupPrefix;
    }

    public boolean isAutoStartProducer() {
        return autoStartProducer;
    }

    public void setAutoStartProducer(boolean autoStartProducer) {
        this.autoStartProducer = autoStartProducer;
    }

    public boolean isAutoStartConsumers() {
        return autoStartConsumers;
    }

    public void setAutoStartConsumers(boolean autoStartConsumers) {
        this.autoStartConsumers = autoStartConsumers;
    }
}
