package io.ddd4j.mq.mqtt.mica.bridge.autoconfigure;

import io.ddd4j.mq.mqtt.mica.bridge.MicaMqttBridgeMarker;
import io.ddd4j.mq.mqtt.mica.bridge.consumer.MicaMqttClientSubscribeRegistrar;
import io.ddd4j.mq.mqtt.mica.bridge.consumer.MicaMqttClientSubscribeScanner;
import io.ddd4j.mq.mqtt.mica.bridge.registry.MicaMqttClientSubscribeDefinitionRegistry;
import org.dromara.mica.mqtt.spring.client.MqttClientSubscribeDetector;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code @EnableMicaMqttBridge} 导入的配置：注册原生 {@code @MqttClientSubscribe} 扫描与 mica 会话订阅。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Configuration(proxyBeanMethods = false)
public class MicaMqttBridgeConfiguration {

    /**
     * 桥接启用标记，用于关闭 no-op 守卫并避免重复装配。
     */
    @Bean
    public MicaMqttBridgeMarker micaMqttBridgeMarker() {
        return new MicaMqttBridgeMarker();
    }

    /**
     * 占位 {@link MqttClientSubscribeDetector}，阻止 mica 默认全局扫描（由本模块 scanner 接管）。
     */
    @Bean
    public MqttClientSubscribeDetector micaMqttBridgeSubscribeDetectorPlaceholder(ApplicationContext applicationContext) {
        return new MqttClientSubscribeDetector(applicationContext) {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                return bean;
            }
        };
    }

    /**
     * 原生订阅定义注册表。
     */
    @Bean
    public MicaMqttClientSubscribeDefinitionRegistry micaMqttClientSubscribeDefinitionRegistry() {
        return new MicaMqttClientSubscribeDefinitionRegistry();
    }

    /**
     * 将扫描结果注册到 mica {@code IMqttClientSession}。
     */
    @Bean(destroyMethod = "close")
    public MicaMqttClientSubscribeRegistrar micaMqttClientSubscribeRegistrar(ApplicationContext applicationContext) {
        return new MicaMqttClientSubscribeRegistrar(applicationContext);
    }

    /**
     * 扫描 {@code @MqttClientSubscribe} 并触发注册。
     */
    @Bean
    public MicaMqttClientSubscribeScanner micaMqttClientSubscribeScanner(
            MicaMqttClientSubscribeDefinitionRegistry registry,
            MicaMqttClientSubscribeRegistrar registrar) {
        return new MicaMqttClientSubscribeScanner(registry, registrar);
    }
}
