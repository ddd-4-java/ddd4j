package io.ddd4j.mq.spring.config;

import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.event.MQEventStorer;
import io.ddd4j.mq.serialization.JsonMQEventSerialization;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * ddd4j MQ 的 Spring 装配配置（从 ddd4j-mq-core 迁出）。
 *
 * <p>纯 Spring {@code @Configuration}，零 Spring Boot 依赖，承担三项职责：
 * <ol>
 *   <li>从 {@link Environment} 把 {@code ddd4j.mq.*} 绑定到 {@link MQProperties}。
 *       core 的 {@link MQProperties} 是纯 POJO（不标注 {@code @ConfigurationProperties}，
 *       保护其零 Spring 依赖的设计目标），故由本桥接层手动绑定全部字段</li>
 *   <li>注册默认 JSON 序列化器</li>
 *   <li>暴露可选的 {@link MQEventStorer}（业务方注册则生效，否则缺失）</li>
 * </ol>
 *
 * <p>本类由 {@link Ddd4jMQRegistrarConfiguration} 配套使用，应用通过组件扫描或
 * {@code @Import} 引入即可生效。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Configuration(proxyBeanMethods = false)
public class Ddd4jMQPropertiesConfiguration {

    private static final String PREFIX = "ddd4j.mq.";

    /**
     * 从 {@link Environment} 绑定 {@code ddd4j.mq.*} 到 {@link MQProperties}。
     *
     * <p>逐字段读取并带默认值，覆盖 {@link MQProperties} 全部 15 个字段；
     * core 后续新增字段需在此同步补一行。
     *
     * @param environment Spring 环境
     * @return MQ 主配置
     */
    @Bean
    public MQProperties ddd4jMQProperties(Environment environment) {
        MQProperties properties = new MQProperties();
        properties.setEnabled(environment.getProperty(PREFIX + "enabled", Boolean.class, false));
        properties.setBroker(environment.getProperty(PREFIX + "broker", "none"));
        properties.setServer(environment.getProperty(PREFIX + "server", ""));
        properties.setNamespace(environment.getProperty(PREFIX + "namespace", ""));
        properties.setPersist(environment.getProperty(PREFIX + "persist", Boolean.class, false));
        properties.setSerialization(environment.getProperty(PREFIX + "serialization", "json"));
        properties.setAutoAck(environment.getProperty(PREFIX + "auto-ack", Boolean.class, false));
        properties.setRetries(environment.getProperty(PREFIX + "retries", Integer.class, 0));
        properties.setUsername(environment.getProperty(PREFIX + "username", ""));
        properties.setPassword(environment.getProperty(PREFIX + "password", ""));
        properties.setDatabase(environment.getProperty(PREFIX + "database"));
        properties.setProducerGroup(environment.getProperty(PREFIX + "producer-group", "DEFAULT"));
        properties.setDefaultTopic(environment.getProperty(PREFIX + "default-topic", "DEFAULT"));
        properties.setExchange(environment.getProperty(PREFIX + "exchange", ""));
        properties.setConcat(environment.getProperty(PREFIX + "concat"));
        return properties;
    }

    /**
     * 默认 JSON 消息序列化 Bean。
     *
     * @return JSON 序列化实现
     */
    @Bean
    public MQEventSerialization mqEventSerialization() {
        return new JsonMQEventSerialization();
    }

    /**
     * MQ 事件持久化端口（可选）。业务方注册 {@link MQEventStorer} Bean 后，
     * 由 {@link io.ddd4j.mq.MQClient#consume} 在消费前自动调用；未注册时返回 {@code null}。
     *
     * @param storerProvider 业务注册的持久化端口
     * @return 持久化端口，未注册时返回 {@code null}
     */
    @Bean
    public MQEventStorer<?> mqEventStorer(ObjectProvider<MQEventStorer<?>> storerProvider) {
        return storerProvider.getIfAvailable();
    }
}
