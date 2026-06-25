package io.ddd4j.boot.cmpt.mqtt.mica.bridge.annotation;

import io.ddd4j.boot.cmpt.mqtt.mica.bridge.autoconfigure.MicaMqttBridgeConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 在 IoT 等模块显式启用 mica 原生 {@code @MqttClientSubscribe} 扫描与订阅注册。
 * <p>
 * 与 {@code ddd4j.mq.broker=mqtt-mica} 下的 {@code @MQEventListener} 路径并存；未标注本注解时不会扫描
 * {@code @MqttClientSubscribe}（由 {@link io.ddd4j.boot.cmpt.mqtt.mica.bridge.guard.NoOpMqttClientSubscribeDetector} 屏蔽 mica 默认检测器）。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(MicaMqttBridgeConfiguration.class)
public @interface EnableMicaMqttBridge {
}
