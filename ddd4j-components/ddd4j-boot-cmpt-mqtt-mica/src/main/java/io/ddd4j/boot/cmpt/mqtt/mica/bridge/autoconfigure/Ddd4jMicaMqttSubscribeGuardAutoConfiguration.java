package io.ddd4j.boot.cmpt.mqtt.mica.bridge.autoconfigure;

import io.ddd4j.boot.cmpt.mqtt.mica.bridge.MicaMqttBridgeMarker;
import io.ddd4j.boot.cmpt.mqtt.mica.bridge.guard.NoOpMqttClientSubscribeDetector;
import org.dromara.mica.mqtt.spring.client.MqttClientSubscribeDetector;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 在 ddd4j mqtt-mica 路径下屏蔽 mica 默认 {@code @MqttClientSubscribe} 全局扫描。
 * <p>
 * 仅当未启用 {@code @EnableMicaMqttBridge}（无 {@link MicaMqttBridgeMarker}）时生效。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(MqttClientSubscribeDetector.class)
@ConditionalOnExpression("${ddd4j.mq.enabled:false} && '${ddd4j.mq.broker:none}' == 'mqtt-mica'")
@ConditionalOnMissingBean(MicaMqttBridgeMarker.class)
@AutoConfigureBefore(name = "org.dromara.mica.mqtt.spring.client.config.MqttClientConfiguration")
public class Ddd4jMicaMqttSubscribeGuardAutoConfiguration {

    /**
     * 注册 no-op 检测器，优先于 mica {@code MqttClientConfiguration}，避免全局扫描 {@code @MqttClientSubscribe}。
     */
    @Bean
    @ConditionalOnMissingBean(MqttClientSubscribeDetector.class)
    public MqttClientSubscribeDetector ddd4jMicaMqttSubscribeDetectorGuard(ApplicationContext applicationContext) {
        return new NoOpMqttClientSubscribeDetector(applicationContext);
    }
}
