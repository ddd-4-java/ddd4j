package io.ddd4j.mq.registry;

import io.ddd4j.mq.config.Ddd4jMQProperties;


/**
 * {@link MQEventListener} 端点物理命名约定（topic / queue / subject 等跨 Broker 复用）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class MQListenerEndpointNaming {

    private MQListenerEndpointNaming() {
    }

    /**
     * 解析连接符，默认 {@code .}。
     */
    public static String resolveConcat(MQListenerDefinition definition) {
        if (java.util.Objects.nonNull(definition) && hasText(definition.getConcat())) {
            return definition.getConcat();
        }
        return ".";
    }

    /**
     * 解析首个正向 tag（复合表达式取首个 include）。
     */
    public static String resolveTag(String tags) {
        return MQTagMatcher.findIncludes(tags).stream().findFirst().orElse(null);
    }

    /**
     * 构建物理 topic：namespace.topic[.tag]。
     */
    public static String physicalTopic(Ddd4jMQProperties properties, MQListenerDefinition definition) {
        String concat = resolveConcat(definition);
        String namespace = hasText(definition.getNamespace())
                ? definition.getNamespace()
                : properties.getNamespace();
        String topic = definition.getTopic();
        String tag = resolveTag(definition.getTags());
        String base = namespace + concat + topic;
        return java.util.Objects.isNull(tag) ? base : base + concat + tag;
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

    private static boolean hasText(String s) {
        return java.util.Objects.nonNull(s) && !io.ddd4j.kit.lang.StrKit.isBlank(s);
    }
}
