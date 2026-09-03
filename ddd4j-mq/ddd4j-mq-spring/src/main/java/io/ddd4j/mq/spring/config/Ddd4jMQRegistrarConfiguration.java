package io.ddd4j.mq.spring.config;

import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.event.MQEventStorer;
import io.ddd4j.mq.spring.registry.MQListenerBeanPostProcessor;
import io.ddd4j.mq.spring.registry.MQListenerRegistrar;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * ddd4j MQ 监听器装配配置（纯 Spring）。
 *
 * <p>声明两个 Spring 容器基础设施 Bean：
 * <ul>
 *   <li>{@link MQListenerBeanPostProcessor} —— Bean 初始化后扫描 {@code @MQEventListener} 收集监听器</li>
 *   <li>{@link MQListenerRegistrar} —— 上下文就绪后驱动 {@link MQClient} 装配</li>
 * </ul>
 *
 * <p>注意：{@link MQListenerBeanPostProcessor} 和 {@link MQListenerRegistrar} 均无 {@code @Component}，
 * 必须由本类显式声明为 Bean 才能纳入 Spring 容器管理。
 *
 * <p>本类不使用 {@code @ConditionalOnProperty}（那是 Spring Boot 专属）。enabled 判断由
 * {@link MQListenerRegistrar#onContextRefreshed} 和 {@link MQClient#init} 内部短路完成，
 * disabled 时 {@link MQListenerBeanPostProcessor} 不扫描、{@link MQListenerRegistrar} 不装配，零开销。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Configuration(proxyBeanMethods = false)
public class Ddd4jMQRegistrarConfiguration {

    /**
     * 注册 {@link MQListenerBeanPostProcessor}，扫描并收集所有 {@code @MQEventListener} 方法。
     *
     * @param properties MQ 主配置
     * @return 监听器收集器
     */
    @Bean
    public MQListenerBeanPostProcessor mqListenerBeanPostProcessor(MQProperties properties) {
        return new MQListenerBeanPostProcessor(properties);
    }

    /**
     * 注册 {@link MQListenerRegistrar}，上下文就绪后驱动 {@link MQClient} 装配。
     *
     * @param beanPostProcessor 监听器收集器
     * @param mqClients         所有已注册的 MQ 客户端实现
     * @param properties        MQ 主配置
     * @param serialization     消息序列化器
     * @param storerProvider    可选的事件持久化器
     * @return MQ 装配驱动器
     */
    @Bean
    public MQListenerRegistrar mqListenerRegistrar(
            MQListenerBeanPostProcessor beanPostProcessor,
            List<MQClient> mqClients,
            MQProperties properties,
            MQEventSerialization serialization,
            ObjectProvider<MQEventStorer<?>> storerProvider) {
        return new MQListenerRegistrar(beanPostProcessor, mqClients, properties, serialization, storerProvider);
    }
}
