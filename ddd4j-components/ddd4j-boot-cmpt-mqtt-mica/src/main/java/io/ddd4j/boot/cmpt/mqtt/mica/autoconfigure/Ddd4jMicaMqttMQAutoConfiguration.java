package io.ddd4j.boot.cmpt.mqtt.mica.autoconfigure;

import io.ddd4j.boot.cmpt.mqtt.mica.config.Ddd4jMicaMqttProperties;
import io.ddd4j.boot.cmpt.mqtt.mica.consumer.MicaMqttMQConsumerEndpointRegistrar;
import io.ddd4j.boot.cmpt.mqtt.mica.publisher.MicaMqttMQEventPublisher;
import io.ddd4j.boot.cmpt.mqtt.mica.spi.MicaMqttMQBrokerAdapter;
import io.ddd4j.boot.mq.config.Ddd4jMQProperties;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import io.ddd4j.boot.mq.spi.MQBrokerAdapter;
import org.dromara.mica.mqtt.spring.client.MqttClientTemplate;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * mica-mqtt 组件自动配置，在 {@code ddd4j.mq.enabled=true} 且 broker=mqtt-mica 时生效。
 * <p>
 * 主路径：mica-mqtt client（与 sample mqtt-client2 一致），连接参数走 {@code mqtt.client.*}。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(MqttClientTemplate.class)
@ConditionalOnExpression("${ddd4j.mq.enabled:false} && '${ddd4j.mq.broker:none}' == 'mqtt-mica'")
@AutoConfigureAfter(name = "org.dromara.mica.mqtt.spring.client.config.MqttClientConfiguration")
@EnableConfigurationProperties({Ddd4jMQProperties.class, Ddd4jMicaMqttProperties.class})
public class Ddd4jMicaMqttMQAutoConfiguration {

    /**
     * 注册 mica-mqtt 消费端点编排器。
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public MicaMqttMQConsumerEndpointRegistrar micaMqttMQConsumerEndpointRegistrar(
            MqttClientTemplate mqttClientTemplate,
            Ddd4jMQProperties mqProperties,
            Ddd4jMicaMqttProperties micaMqttProperties) {
        return new MicaMqttMQConsumerEndpointRegistrar(mqttClientTemplate, mqProperties, micaMqttProperties);
    }

    /**
     * 注册 mica-mqtt Broker 适配器。
     */
    @Bean
    @ConditionalOnMissingBean(MQBrokerAdapter.class)
    public MicaMqttMQBrokerAdapter micaMqttMQBrokerAdapter(
            MqttClientTemplate mqttClientTemplate,
            Ddd4jMQProperties mqProperties,
            Ddd4jMicaMqttProperties micaMqttProperties,
            MicaMqttMQConsumerEndpointRegistrar consumerEndpointRegistrar) {
        return new MicaMqttMQBrokerAdapter(
                mqttClientTemplate, mqProperties, micaMqttProperties.getQos(), consumerEndpointRegistrar);
    }

    /**
     * 注册领域事件发布 Bean。
     */
    @Bean
    @ConditionalOnMissingBean(MQEventPublisher.class)
    public MQEventPublisher micaMqttMQEventPublisher(
            MqttClientTemplate mqttClientTemplate,
            Ddd4jMQProperties mqProperties,
            Ddd4jMicaMqttProperties micaMqttProperties) {
        return new MicaMqttMQEventPublisher(mqttClientTemplate, mqProperties, micaMqttProperties.getQos());
    }
}
