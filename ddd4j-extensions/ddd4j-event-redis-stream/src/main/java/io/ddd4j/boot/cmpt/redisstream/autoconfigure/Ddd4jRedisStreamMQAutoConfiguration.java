package io.ddd4j.boot.cmpt.redisstream.autoconfigure;

import io.ddd4j.boot.cmpt.redisstream.consumer.RedisStreamConsumerEndpointRegistrar;
import io.ddd4j.boot.cmpt.redisstream.publisher.RedisStreamMQEventPublisher;
import io.ddd4j.boot.cmpt.redisstream.spi.RedisStreamMQBrokerAdapter;
import io.ddd4j.boot.mq.config.Ddd4jMQProperties;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import io.ddd4j.boot.mq.spi.MQBrokerAdapter;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis Stream 组件自动配置，在 {@code ddd4j.mq.enabled=true} 且 broker=redis-stream/redis 时生效。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnExpression("'${ddd4j.mq.enabled:false}' == 'true' && ('${ddd4j.mq.broker:none}' == 'redis-stream' || '${ddd4j.mq.broker:none}' == 'redis')")
@AutoConfigureAfter(RedisAutoConfiguration.class)
@EnableConfigurationProperties(Ddd4jMQProperties.class)
public class Ddd4jRedisStreamMQAutoConfiguration {

    /**
     * 注册 Redis Stream 消费端点编排器。
     */
    @Bean
    @ConditionalOnMissingBean
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
    @ConditionalOnMissingBean(MQBrokerAdapter.class)
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
    @ConditionalOnMissingBean(MQEventPublisher.class)
    public MQEventPublisher redisStreamMQEventPublisher(
            StringRedisTemplate stringRedisTemplate,
            Ddd4jMQProperties properties) {
        return new RedisStreamMQEventPublisher(stringRedisTemplate, properties);
    }
}
