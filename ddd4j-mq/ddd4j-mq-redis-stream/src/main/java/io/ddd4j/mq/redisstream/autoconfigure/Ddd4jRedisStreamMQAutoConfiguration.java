package io.ddd4j.mq.redisstream.autoconfigure;

import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.redisstream.consumer.RedisStreamConsumerEndpointRegistrar;
import io.ddd4j.mq.redisstream.publisher.RedisStreamMQEventPublisher;
import io.ddd4j.mq.redisstream.spi.RedisStreamMQBrokerAdapter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis Stream 组件自动配置，在 {@code ddd4j.mq.enabled=true} 且 broker=redis-stream/redis 时生效。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Configuration(proxyBeanMethods = false)
public class Ddd4jRedisStreamMQAutoConfiguration {

    /**
     * 注册 Redis Stream 消费端点编排器。
     */
    @Bean
    public RedisStreamConsumerEndpointRegistrar redisStreamConsumerEndpointRegistrar(
            ApplicationContext applicationContext,
            Ddd4jMQProperties properties,
            StringRedisTemplate stringRedisTemplate) {
        return new RedisStreamConsumerEndpointRegistrar(applicationContext, properties, stringRedisTemplate);
    }

    /**
     * 注册 Redis Stream Broker 适配器。
     */
    @Bean
    public RedisStreamMQBrokerAdapter redisStreamMQBrokerAdapter(
            StringRedisTemplate stringRedisTemplate,
            Ddd4jMQProperties properties,
            RedisStreamConsumerEndpointRegistrar consumerEndpointRegistrar) {
        return new RedisStreamMQBrokerAdapter(stringRedisTemplate, properties, consumerEndpointRegistrar);
    }

    /**
     * 注册领域事件发布 Bean。
     */
    @Bean
    public MQEventPublisher redisStreamMQEventPublisher(
            StringRedisTemplate stringRedisTemplate,
            Ddd4jMQProperties properties) {
        return new RedisStreamMQEventPublisher(stringRedisTemplate, properties);
    }
}
