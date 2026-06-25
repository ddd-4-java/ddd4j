package io.ddd4j.boot.cmpt.ons.autoconfigure;

import com.aliyun.openservices.ons.api.ONSFactory;
import com.aliyun.openservices.ons.api.Producer;
import com.aliyun.openservices.ons.api.PropertyKeyConst;
import io.ddd4j.boot.cmpt.ons.consumer.OnsMQConsumerEndpointRegistrar;
import io.ddd4j.boot.cmpt.ons.publisher.OnsMQEventPublisher;
import io.ddd4j.boot.cmpt.ons.spi.OnsMQBrokerAdapter;
import io.ddd4j.boot.mq.config.Ddd4jMQProperties;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import io.ddd4j.boot.mq.spi.MQBrokerAdapter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * 阿里云 ONS 组件自动配置，在 {@code ddd4j.mq.enabled=true} 且 broker=ons 时生效。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(Producer.class)
@ConditionalOnExpression("${ddd4j.mq.enabled:false} == true && '${ddd4j.mq.broker:none}'.equals('ons')")
@EnableConfigurationProperties(Ddd4jMQProperties.class)
public class Ddd4jOnsMQAutoConfiguration {

    /**
     * ONS 连接属性（Producer / Consumer 共用）。
     */
    @Bean
    @ConditionalOnMissingBean(name = "onsConnectionProperties")
    public Properties onsConnectionProperties(
            @Value("${ddd4j.mq.ons.access-key:}") String accessKey,
            @Value("${ddd4j.mq.ons.secret-key:}") String secretKey,
            @Value("${ddd4j.mq.ons.namesrv-addr:}") String nameSrvAddr) {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.AccessKey, accessKey);
        properties.setProperty(PropertyKeyConst.SecretKey, secretKey);
        properties.setProperty(PropertyKeyConst.NAMESRV_ADDR, nameSrvAddr);
        return properties;
    }

    /**
     * 注册 ONS Producer。
     */
    @Bean(initMethod = "start", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(Producer.class)
    public Producer onsProducer(
            Properties onsConnectionProperties,
            @Value("${ddd4j.mq.ons.producer-group:DEFAULT}") String groupId) {
        Properties properties = new Properties();
        properties.putAll(onsConnectionProperties);
        properties.setProperty(PropertyKeyConst.GROUP_ID, groupId);
        return ONSFactory.createProducer(properties);
    }

    /**
     * 注册 ONS 消费端点编排器。
     */
    @Bean
    @ConditionalOnMissingBean
    public OnsMQConsumerEndpointRegistrar onsMQConsumerEndpointRegistrar(
            Properties onsConnectionProperties,
            Ddd4jMQProperties properties) {
        return new OnsMQConsumerEndpointRegistrar(onsConnectionProperties, properties);
    }

    /**
     * 注册 ONS Broker 适配器。
     */
    @Bean
    @ConditionalOnMissingBean(MQBrokerAdapter.class)
    public OnsMQBrokerAdapter onsMQBrokerAdapter(
            ObjectProvider<Producer> producerProvider,
            Ddd4jMQProperties properties,
            OnsMQConsumerEndpointRegistrar consumerEndpointRegistrar) {
        return new OnsMQBrokerAdapter(producerProvider.getIfAvailable(), properties, consumerEndpointRegistrar);
    }

    /**
     * 注册领域事件发布 Bean。
     */
    @Bean
    @ConditionalOnMissingBean(MQEventPublisher.class)
    public MQEventPublisher onsMQEventPublisher(
            ObjectProvider<Producer> producerProvider,
            Ddd4jMQProperties properties) {
        return new OnsMQEventPublisher(producerProvider.getIfAvailable(), properties);
    }
}
