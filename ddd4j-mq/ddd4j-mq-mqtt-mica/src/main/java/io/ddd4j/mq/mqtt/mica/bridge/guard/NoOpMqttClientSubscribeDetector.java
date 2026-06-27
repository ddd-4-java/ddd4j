package io.ddd4j.mq.mqtt.mica.bridge.guard;

import org.dromara.mica.mqtt.spring.client.MqttClientSubscribeDetector;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

/**
 * 屏蔽 mica 默认 {@link MqttClientSubscribeDetector} 的全局 {@code @MqttClientSubscribe} 扫描。
 * <p>
 * 在未启用 {@code @EnableMicaMqttBridge} 时由守卫自动配置注册，避免与 ddd4j {@code @MQEventListener} 路径混用。
 */
public class NoOpMqttClientSubscribeDetector extends MqttClientSubscribeDetector {

    public NoOpMqttClientSubscribeDetector(ApplicationContext applicationContext) {
        super(applicationContext);
    }

    /**
     * 不扫描、不注册任何 {@code @MqttClientSubscribe} 订阅。
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }
}
