package io.ddd4j.mq.rabbit;

import com.rabbitmq.client.Channel;

/**
 * Provides RabbitMQ channels to publisher and consumer registrar.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@FunctionalInterface
public interface RabbitChannelProvider {

    Channel channel() throws Exception;
}
