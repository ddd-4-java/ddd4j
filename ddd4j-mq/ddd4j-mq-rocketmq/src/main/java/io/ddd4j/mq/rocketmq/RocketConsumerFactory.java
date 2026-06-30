package io.ddd4j.mq.rocketmq;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;

/**
 * Creates RocketMQ push consumers.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@FunctionalInterface
public interface RocketConsumerFactory {

    DefaultMQPushConsumer create(String group);

}
