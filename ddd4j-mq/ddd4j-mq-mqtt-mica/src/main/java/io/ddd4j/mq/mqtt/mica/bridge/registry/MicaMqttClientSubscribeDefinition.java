package io.ddd4j.mq.mqtt.mica.bridge.registry;

import lombok.Builder;
import lombok.Getter;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.annotation.MqttClientSubscribe;
import org.dromara.mica.mqtt.core.deserialize.MqttDeserializer;

import java.lang.reflect.Method;

/**
 * {@link MqttClientSubscribe} 解析后的原生订阅定义。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Getter
@Builder
public class MicaMqttClientSubscribeDefinition {

    private final Object bean;
    private final String beanName;
    private final Method method;
    private final String[] topicTemplates;
    private final String[] topicFilters;
    private final MqttQoS qos;
    private final String clientTemplateBean;
    private final Class<? extends MqttDeserializer> deserializerType;
    private final boolean classLevelListener;

    /**
     * 从方法级注解构建定义。
     */
    public static MicaMqttClientSubscribeDefinition fromMethod(
            Object bean,
            String beanName,
            Method method,
            MqttClientSubscribe subscribe,
            String[] topicFilters) {
        return MicaMqttClientSubscribeDefinition.builder()
                .bean(bean)
                .beanName(beanName)
                .method(method)
                .topicTemplates(subscribe.value())
                .topicFilters(topicFilters)
                .qos(subscribe.qos())
                .clientTemplateBean(subscribe.clientTemplateBean())
                .deserializerType(subscribe.deserialize())
                .classLevelListener(false)
                .build();
    }

    /**
     * 从类级注解构建定义（{@code IMqttClientMessageListener} 实现类）。
     */
    public static MicaMqttClientSubscribeDefinition fromClass(
            Object bean,
            String beanName,
            MqttClientSubscribe subscribe,
            String[] topicFilters) {
        return MicaMqttClientSubscribeDefinition.builder()
                .bean(bean)
                .beanName(beanName)
                .method(null)
                .topicTemplates(subscribe.value())
                .topicFilters(topicFilters)
                .qos(subscribe.qos())
                .clientTemplateBean(subscribe.clientTemplateBean())
                .deserializerType(subscribe.deserialize())
                .classLevelListener(true)
                .build();
    }
}
