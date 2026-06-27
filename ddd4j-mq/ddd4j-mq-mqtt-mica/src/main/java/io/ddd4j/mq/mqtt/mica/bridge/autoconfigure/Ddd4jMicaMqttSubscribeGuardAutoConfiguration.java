package io.ddd4j.mq.mqtt.mica.bridge.autoconfigure;

import io.ddd4j.mq.mqtt.mica.bridge.MicaMqttBridgeMarker;
import io.ddd4j.mq.mqtt.mica.bridge.guard.NoOpMqttClientSubscribeDetector;
import org.dromara.mica.mqtt.spring.client.MqttClientSubscribeDetector;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 在 ddd4j mqtt-mica 路径下屏蔽 mica 默认 {@code @MqttClientSubscribe} 全局扫描。
 * <p>
 * 仅当未启用 {@code @EnableMicaMqttBridge}（无 {@link MicaMqttBridgeMarker}）时生效。
 */
@Configuration(proxyBeanMethods = false)
public class Ddd4jMicaMqttSubscribeGuardAutoConfiguration {

    /**
     * 注册 no-op 检测器，优先于 mica {@code MqttClientConfiguration}，避免全局扫描 {@code @MqttClientSubscribe}。
     */
    @Bean
    public MqttClientSubscribeDetector ddd4jMicaMqttSubscribeDetectorGuard(ApplicationContext applicationContext) {
        return new NoOpMqttClientSubscribeDetector(applicationContext);
    }
}
