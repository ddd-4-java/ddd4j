package io.ddd4j.mq.mqtt.mica.bridge.annotation;

import io.ddd4j.mq.mqtt.mica.bridge.autoconfigure.MicaMqttBridgeConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 在 IoT 等模块显式启用 mica 原生 {@code @MqttClientSubscribe} 扫描与订阅注册。
 * <p>
 * 与 {@code ddd4j.mq.broker=mqtt-mica} 下的 {@code @MQEventListener} 路径并存；未标注本注解时不会扫描
 * {@code @MqttClientSubscribe}（由 {@link io.ddd4j.mq.mqtt.mica.bridge.guard.NoOpMqttClientSubscribeDetector} 屏蔽 mica 默认检测器）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(MicaMqttBridgeConfiguration.class)
public @interface EnableMicaMqttBridge {
}
