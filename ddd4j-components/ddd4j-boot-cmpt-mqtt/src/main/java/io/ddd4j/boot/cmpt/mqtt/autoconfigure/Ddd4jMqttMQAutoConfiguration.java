package io.ddd4j.boot.cmpt.mqtt.autoconfigure;

import io.ddd4j.boot.cmpt.mqtt.config.Ddd4jMqttProperties;
import io.ddd4j.boot.cmpt.mqtt.consumer.MqttMQConsumerEndpointRegistrar;
import io.ddd4j.boot.cmpt.mqtt.publisher.MqttMQEventPublisher;
import io.ddd4j.boot.cmpt.mqtt.spi.MqttMQBrokerAdapter;
import io.ddd4j.boot.mq.config.Ddd4jMQProperties;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import io.ddd4j.boot.mq.spi.MQBrokerAdapter;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * MQTT 组件自动配置，在 {@code ddd4j.mq.enabled=true} 且 broker=mqtt 时生效。
 * <p>
 * 主路径：Eclipse Paho + Spring Integration（与 sample mqtt-client1 一致）。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(MqttPahoClientFactory.class)
@ConditionalOnExpression("${ddd4j.mq.enabled:false} && '${ddd4j.mq.broker:none}' == 'mqtt'")
@EnableConfigurationProperties({Ddd4jMQProperties.class, Ddd4jMqttProperties.class})
public class Ddd4jMqttMQAutoConfiguration {

    /**
     * 注册 Paho 客户端工厂。
     */
    @Bean
    @ConditionalOnMissingBean(MqttPahoClientFactory.class)
    public MqttPahoClientFactory mqttPahoClientFactory(Ddd4jMqttProperties mqttProperties) {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{mqttProperties.getUrl()});
        if (StringUtils.hasText(mqttProperties.getUsername())) {
            options.setUserName(mqttProperties.getUsername());
        }
        if (StringUtils.hasText(mqttProperties.getPassword())) {
            options.setPassword(mqttProperties.getPassword().toCharArray());
        }
        options.setCleanSession(mqttProperties.isCleanSession());
        options.setConnectionTimeout(mqttProperties.getConnectionTimeout());
        options.setKeepAliveInterval(mqttProperties.getKeepAliveInterval());
        options.setAutomaticReconnect(mqttProperties.isAutomaticReconnect());
        options.setMaxReconnectDelay(mqttProperties.getMaxReconnectDelay());
        factory.setConnectionOptions(options);
        return factory;
    }

    /**
     * MQTT 出站通道。
     */
    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    /**
     * MQTT 出站消息处理器（发布）。
     */
    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutboundHandler(
            MqttPahoClientFactory mqttClientFactory,
            Ddd4jMqttProperties mqttProperties) {
        String clientId = mqttProperties.getPublishClientIdPrefix() + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        MqttPahoMessageHandler handler = new MqttPahoMessageHandler(clientId, mqttClientFactory);
        handler.setAsync(mqttProperties.isAsyncPublish());
        handler.setDefaultQos(mqttProperties.getQos());
        handler.setConverter(new DefaultPahoMessageConverter());
        return handler;
    }

    /**
     * 注册 MQTT 消费端点编排器。
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public MqttMQConsumerEndpointRegistrar mqttMQConsumerEndpointRegistrar(
            MqttPahoClientFactory mqttClientFactory,
            Ddd4jMQProperties mqProperties,
            Ddd4jMqttProperties mqttProperties) {
        return new MqttMQConsumerEndpointRegistrar(mqttClientFactory, mqProperties, mqttProperties);
    }

    /**
     * 注册 MQTT Broker 适配器。
     */
    @Bean
    @ConditionalOnMissingBean(MQBrokerAdapter.class)
    public MqttMQBrokerAdapter mqttMQBrokerAdapter(
            MessageChannel mqttOutboundChannel,
            Ddd4jMQProperties mqProperties,
            Ddd4jMqttProperties mqttProperties,
            MqttMQConsumerEndpointRegistrar consumerEndpointRegistrar) {
        return new MqttMQBrokerAdapter(
                mqttOutboundChannel, mqProperties, mqttProperties.getQos(), consumerEndpointRegistrar);
    }

    /**
     * 注册领域事件发布 Bean。
     */
    @Bean
    @ConditionalOnMissingBean(MQEventPublisher.class)
    public MQEventPublisher mqttMQEventPublisher(
            MessageChannel mqttOutboundChannel,
            Ddd4jMQProperties mqProperties,
            Ddd4jMqttProperties mqttProperties) {
        return new MqttMQEventPublisher(mqttOutboundChannel, mqProperties, mqttProperties.getQos());
    }
}
