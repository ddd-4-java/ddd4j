package io.ddd4j.mq.spring.config;

import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumeInterceptor;
import io.ddd4j.mq.serialization.JsonMQMessageSerialization;
import io.ddd4j.mq.serialization.MQMessageSerialization;
import io.ddd4j.mq.store.MQEventPersistInterceptor;
import io.ddd4j.mq.store.MQEventStorer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Spring 版 MQ 属性与通用 Bean 配置（从 ddd4j-mq-core 迁出到 ddd4j-mq-spring）。
 * <p>
 * 供纯 Spring {@code @Configuration} 装配使用。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Configuration(proxyBeanMethods = false)
public class Ddd4jMQPropertiesConfiguration {

    /**
     * 从 {@link Environment} 绑定 {@code ddd4j.mq.*} 主配置。
     *
     * @param environment Spring 环境
     * @return MQ 主配置
     */
    @Bean
    public Ddd4jMQProperties ddd4jMQProperties(Environment environment) {
        Ddd4jMQProperties properties = new Ddd4jMQProperties();
        properties.setEnabled(Boolean.parseBoolean(environment.getProperty("ddd4j.mq.enabled", "false")));
        properties.setBroker(environment.getProperty("ddd4j.mq.broker", "none"));
        properties.setNamespace(environment.getProperty("ddd4j.mq.namespace", ""));
        properties.setDefaultTopic(environment.getProperty("ddd4j.mq.default-topic", "DEFAULT"));
        properties.setSerialization(environment.getProperty("ddd4j.mq.serialization", "json"));
        properties.setPersist(Boolean.parseBoolean(environment.getProperty("ddd4j.mq.persist", "false")));
        properties.setRetries(Integer.parseInt(environment.getProperty("ddd4j.mq.retries", "0")));
        return properties;
    }

    /**
     * 默认 JSON 消息序列化 Bean。
     *
     * @return JSON 序列化实现
     */
    @Bean
    public MQMessageSerialization mqMessageSerialization() {
        return new JsonMQMessageSerialization();
    }

    /**
     * MQ 事件持久化拦截器；没有 {@link MQEventStorer} 时自动 no-op。
     *
     * @param properties     MQ 主配置
     * @param storerProvider 业务注册的持久化端口
     * @return 消费拦截器
     */
    @Bean
    @SuppressWarnings("rawtypes")
    public MQConsumeInterceptor mqEventPersistInterceptor(
            Ddd4jMQProperties properties,
            ObjectProvider<MQEventStorer> storerProvider) {
        return new MQEventPersistInterceptor(properties, storerProvider.getIfAvailable());
    }
}
