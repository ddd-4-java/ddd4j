package io.ddd4j.mq.registry;

import io.ddd4j.mq.config.Ddd4jMQProperties;
import org.springframework.util.StringUtils;

/**
 * {@link MQEventListener} 端点物理命名约定（topic / queue / subject 等跨 Broker 复用）。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class MQListenerEndpointNaming {

    private MQListenerEndpointNaming() {
    }

    /**
     * 解析连接符，默认 {@code .}。
     */
    public static String resolveConcat(MQListenerDefinition definition) {
        if (definition != null && StringUtils.hasText(definition.getConcat())) {
            return definition.getConcat();
        }
        return ".";
    }

    /**
     * 解析首个 tag（复合表达式取首段）。
     */
    public static String resolveTag(String tags) {
        if (!StringUtils.hasText(tags) || "*".equals(tags.trim())) {
            return null;
        }
        String trimmed = tags.trim();
        int split = trimmed.indexOf("||");
        return split > 0 ? trimmed.substring(0, split).trim() : trimmed;
    }

    /**
     * 构建物理 topic：namespace.topic[.tag]。
     */
    public static String physicalTopic(Ddd4jMQProperties properties, MQListenerDefinition definition) {
        String concat = resolveConcat(definition);
        String namespace = StringUtils.hasText(definition.getNamespace())
                ? definition.getNamespace()
                : properties.getNamespace();
        String topic = definition.getTopic();
        String tag = resolveTag(definition.getTags());
        String base = namespace + concat + topic;
        return tag == null ? base : base + concat + tag;
    }

    /**
     * 构建队列名：group.namespace.className.methodName（Rabbit / JMS 等）。
     */
    public static String queueName(MQListenerDefinition definition) {
        String concat = resolveConcat(definition);
        String group = definition.getGroup();
        String namespace = definition.getNamespace();
        String className = definition.getMethod().getDeclaringClass().getSimpleName();
        String methodName = definition.getMethod().getName();
        return group + concat + namespace + concat + className + concat + methodName;
    }

    /**
     * 构建端点 ID，保证在容器 registry 内唯一。
     */
    public static String endpointId(String brokerPrefix, MQListenerDefinition definition) {
        return "ddd4j-" + brokerPrefix + "-" + definition.bindingName() + "-"
                + definition.getMethod().getDeclaringClass().getSimpleName() + "-"
                + definition.getMethod().getName();
    }
}
