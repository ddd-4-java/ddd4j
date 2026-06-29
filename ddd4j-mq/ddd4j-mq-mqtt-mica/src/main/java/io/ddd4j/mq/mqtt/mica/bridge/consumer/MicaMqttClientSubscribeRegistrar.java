package io.ddd4j.mq.mqtt.mica.bridge.consumer;

import io.ddd4j.mq.mqtt.mica.bridge.registry.MicaMqttClientSubscribeDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.mica.mqtt.core.client.IMqttClientMessageListener;
import org.dromara.mica.mqtt.core.client.IMqttClientSession;
import org.dromara.mica.mqtt.core.deserialize.MqttDeserializer;
import org.dromara.mica.mqtt.core.util.TopicUtil;
import org.dromara.mica.mqtt.spring.client.MqttClientSubscribeListener;
import org.dromara.mica.mqtt.spring.client.MqttClientTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 将 {@link MqttClientSubscribe} 定义编程式注册到 mica {@link IMqttClientSession}。
 * <p>
 * 注册 API 与 mica 内置 {@code MqttClientSubscribeDetector} 一致：
 * {@code clientSession.addSubscriptionList(topicFilters, qos, listener)}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@RequiredArgsConstructor
public class MicaMqttClientSubscribeRegistrar implements AutoCloseable {

    private final ApplicationContext applicationContext;
    private final List<MicaMqttClientSubscribeDefinition> registeredDefinitions = new CopyOnWriteArrayList<>();

    /**
     * 注册单条 {@link MqttClientSubscribe} 定义到 mica 客户端会话。
     */
    public void register(MicaMqttClientSubscribeDefinition definition) {
        Objects.requireNonNull(definition, "definition");

        IMqttClientSession clientSession = getMqttClientSession(definition.getClientTemplateBean());
        String[] topicFilters = definition.getTopicFilters();
        org.dromara.mica.mqtt.codec.MqttQoS qos = definition.getQos();

        // 逻辑块：类级 IMqttClientMessageListener 与 方法级监听器分支
        if (definition.isClassLevelListener()) {
            clientSession.addSubscriptionList(topicFilters, qos, (IMqttClientMessageListener) definition.getBean());
            log.info("Registered mica @MqttClientSubscribe (class-level): bean={}, topics={}, qos={}",
                    beanLabel(definition), Arrays.toString(topicFilters), qos);
        } else {
            validateMethodSubscription(definition.getMethod());
            MqttDeserializer deserializer = getMqttDeserializer(definition.getDeserializerType());
            MqttClientSubscribeListener listener = new MqttClientSubscribeListener(
                    definition.getBean(),
                    definition.getMethod(),
                    definition.getTopicTemplates(),
                    topicFilters,
                    deserializer);
            clientSession.addSubscriptionList(topicFilters, qos, listener);
            log.info("Registered mica @MqttClientSubscribe: bean={}, method={}, topics={}, qos={}",
                    beanLabel(definition), definition.getMethod().getName(), Arrays.toString(topicFilters), qos);
        }

        registeredDefinitions.add(definition);
    }

    /**
     * 解析 topic 模板为 MQTT filter（Spring 占位符 + {@code ${}} 变量替换）。
     */
    public String[] resolveTopicFilters(String[] topicTemplates) {
        Environment environment = applicationContext.getEnvironment();
        return Arrays.stream(topicTemplates)
                .map(environment::resolvePlaceholders)
                .map(TopicUtil::getTopicFilter)
                .toArray(String[]::new);
    }

    /**
     * 返回已注册定义（只读视图）。
     */
    public List<MicaMqttClientSubscribeDefinition> registeredDefinitions() {
        return List.copyOf(registeredDefinitions);
    }

    @Override
    public void close() {
        registeredDefinitions.clear();
    }

    /**
     * 读取目标 {@link MqttClientTemplate} 的客户端会话。
     */
    private IMqttClientSession getMqttClientSession(String beanName) {
        String resolvedBeanName = applicationContext.getEnvironment().resolvePlaceholders(beanName);
        return applicationContext.getBean(resolvedBeanName, MqttClientTemplate.class)
                .getClientCreator()
                .getClientSession();
    }

    /**
     * 获取反序列化器（优先 Spring Bean，否则无参构造实例化）。
     */
    @SuppressWarnings("unchecked")
    private MqttDeserializer getMqttDeserializer(Class<? extends MqttDeserializer> deserializerType) {
        Class<MqttDeserializer> type = (Class<MqttDeserializer>) deserializerType;
        return applicationContext.getBeanProvider(type)
                .getIfAvailable(() -> BeanUtils.instantiateClass(type));
    }

    /**
     * 校验方法级订阅签名（public、非 static、2~3 个参数），与 mica 内置规则一致。
     */
    private void validateMethodSubscription(java.lang.reflect.Method method) {
        int modifiers = method.getModifiers();
        if (Modifier.isStatic(modifiers)) {
            throw new IllegalArgumentException("@MqttClientSubscribe on method " + method + " must not static.");
        }
        if (!Modifier.isPublic(modifiers)) {
            throw new IllegalArgumentException("@MqttClientSubscribe on method " + method + " must public.");
        }
        int paramCount = method.getParameterCount();
        if (paramCount < 2 || paramCount > 3) {
            throw new IllegalArgumentException("@MqttClientSubscribe on method " + method + " parameter count must 2 ~ 3.");
        }
        if (!method.canAccess(null) && !method.trySetAccessible()) {
            method.setAccessible(true);
        }
    }

    private String beanLabel(MicaMqttClientSubscribeDefinition definition) {
        if (definition.getBeanName() != null) {
            return definition.getBeanName();
        }
        if (definition.getBean() != null) {
            return definition.getBean().getClass().getSimpleName();
        }
        return "unknown";
    }
}
