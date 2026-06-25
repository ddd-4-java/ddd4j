package io.ddd4j.boot.mq.config;

import io.ddd4j.boot.mq.consume.MQConsumeInterceptor;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import io.ddd4j.boot.mq.registry.MQListenerBeanPostProcessor;
import io.ddd4j.boot.mq.registry.MQListenerDefinitionRegistry;
import io.ddd4j.boot.mq.registry.MQListenerRegistrar;
import io.ddd4j.boot.mq.registry.MQListenerScanner;
import io.ddd4j.boot.mq.serialization.JsonMQMessageSerialization;
import io.ddd4j.boot.mq.serialization.MQEventSerialization;
import io.ddd4j.boot.mq.serialization.MQMessageSerialization;
import io.ddd4j.boot.mq.spi.MQBrokerAdapter;
import io.ddd4j.boot.mq.spi.MQBrokerAdapters;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * ddd4j 消息队列自动配置（契约层）：注册属性、发布器与监听器编排。
 */
@Configuration
// @EnableConfigurationProperties(Ddd4jMQProperties.class)
public class Ddd4jMQAutoConfiguration {

    /**
     * 默认 JSON 序列化 Bean。
     */
    @Bean
    // @ConditionalOnMissingBean(MQMessageSerialization.class)
    public MQMessageSerialization mqMessageSerialization() {
        return new JsonMQMessageSerialization();
    }

    /**
     * 注册领域事件发布 Bean。
     */
    @Bean
    // @ConditionalOnBean(MQBrokerAdapter.class)
    // @ConditionalOnMissingBean(MQEventPublisher.class)
    public MQEventPublisher mqEventPublisher(List<MQBrokerAdapter> adapters, Ddd4jMQProperties props) {
        return MQBrokerAdapters.createPublisher(adapters, props);
    }

    /**
     * 监听器定义注册表（由 BeanPostProcessor 填充）。
     */
    @Bean
    // @ConditionalOnBean(MQBrokerAdapter.class)
    public MQListenerDefinitionRegistry mqListenerDefinitionRegistry() {
        return new MQListenerDefinitionRegistry();
    }

    /**
     * 基于 BeanPostProcessor 发现 {@code @MQEventListener} 方法。
     */
    @Bean
    // @ConditionalOnBean(MQBrokerAdapter.class)
    public MQListenerBeanPostProcessor mqListenerBeanPostProcessor(
            MQListenerDefinitionRegistry registry,
            Ddd4jMQProperties props) {
        return new MQListenerBeanPostProcessor(registry, props);
    }

    /**
     * 监听器定义访问门面（读取 Registry）。
     */
    @Bean
    // @ConditionalOnBean(MQBrokerAdapter.class)
    public MQListenerScanner mqListenerScanner(MQListenerDefinitionRegistry registry) {
        return new MQListenerScanner(registry);
    }

    /**
     * 应用就绪后动态注册消费端点到 {@link MQBrokerAdapter}。
     */
    @Bean
    // @ConditionalOnBean(MQBrokerAdapter.class)
    public MQListenerRegistrar mqListenerRegistrar(
            MQListenerScanner scanner,
            List<MQBrokerAdapter> adapters,
            Ddd4jMQProperties props,
            ObjectProvider<MQEventSerialization> serializationProvider,
            ObjectProvider<MQConsumeInterceptor> interceptorsProvider) {

        MQEventSerialization serialization = serializationProvider.getIfAvailable(JsonMQMessageSerialization::new);
        List<MQConsumeInterceptor> interceptors = interceptorsProvider.orderedStream().toList();
        return new MQListenerRegistrar(scanner, adapters, props, serialization, interceptors);
    }
}
