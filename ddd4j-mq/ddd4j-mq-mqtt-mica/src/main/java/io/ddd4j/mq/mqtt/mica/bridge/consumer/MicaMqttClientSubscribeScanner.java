package io.ddd4j.mq.mqtt.mica.bridge.consumer;

import io.ddd4j.mq.mqtt.mica.bridge.registry.MicaMqttClientSubscribeDefinition;
import io.ddd4j.mq.mqtt.mica.bridge.registry.MicaMqttClientSubscribeDefinitionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.mica.mqtt.core.annotation.MqttClientSubscribe;
import org.dromara.mica.mqtt.core.client.IMqttClientMessageListener;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.NonNull;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;

/**
 * 扫描应用上下文中 {@link MqttClientSubscribe} 并委托 {@link MicaMqttClientSubscribeRegistrar} 注册订阅。
 * <p>
 * 仅在 {@code @EnableMicaMqttBridge} 启用时装配；与 ddd4j {@code @MQEventListener} 路径互不干扰。
 */
@Slf4j
@RequiredArgsConstructor
public class MicaMqttClientSubscribeScanner implements BeanPostProcessor, Ordered {

    private final MicaMqttClientSubscribeDefinitionRegistry registry;
    private final MicaMqttClientSubscribeRegistrar registrar;

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    /**
     * Bean 初始化完成后扫描 {@link MqttClientSubscribe} 注解并注册 mica 订阅。
     */
    @Override
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
        Class<?> userClass = ClassUtils.getUserClass(AopUtils.getTargetClass(bean));
        if (isInfrastructureClass(userClass)) {
            return bean;
        }

        // 逻辑块：类级 IMqttClientMessageListener 与方法级订阅
        if (bean instanceof IMqttClientMessageListener) {
            processClassLevelSubscription(userClass, bean, beanName);
        } else {
            processMethodLevelSubscriptions(userClass, bean, beanName);
        }
        return bean;
    }

    /**
     * 返回已扫描到的订阅定义（门面，供测试与诊断使用）。
     */
    public java.util.List<MicaMqttClientSubscribeDefinition> scan() {
        return registry.definitions();
    }

    /**
     * 处理类上的 {@link MqttClientSubscribe}。
     */
    private void processClassLevelSubscription(Class<?> userClass, Object bean, String beanName) {
        MqttClientSubscribe subscribe = AnnotationUtils.findAnnotation(userClass, MqttClientSubscribe.class);
        if (subscribe == null) {
            return;
        }
        String[] topicFilters = registrar.resolveTopicFilters(subscribe.value());
        MicaMqttClientSubscribeDefinition definition =
                MicaMqttClientSubscribeDefinition.fromClass(bean, beanName, subscribe, topicFilters);
        registry.register(definition);
        registrar.register(definition);
    }

    /**
     * 处理方法上的 {@link MqttClientSubscribe}。
     */
    private void processMethodLevelSubscriptions(Class<?> userClass, Object bean, String beanName) {
        ReflectionUtils.doWithMethods(userClass, method -> {
            MqttClientSubscribe subscribe = AnnotationUtils.findAnnotation(method, MqttClientSubscribe.class);
            if (subscribe == null) {
                return;
            }
            String[] topicFilters = registrar.resolveTopicFilters(subscribe.value());
            MicaMqttClientSubscribeDefinition definition =
                    MicaMqttClientSubscribeDefinition.fromMethod(bean, beanName, method, subscribe, topicFilters);
            registry.register(definition);
            registrar.register(definition);
        }, ReflectionUtils.USER_DECLARED_METHODS);
    }

    /**
     * 过滤 Spring 基础设施类，避免误扫描。
     */
    private boolean isInfrastructureClass(Class<?> clazz) {
        String name = clazz.getName();
        return name.startsWith("org.springframework")
                || name.startsWith("java.")
                || name.startsWith("jakarta.");
    }
}
