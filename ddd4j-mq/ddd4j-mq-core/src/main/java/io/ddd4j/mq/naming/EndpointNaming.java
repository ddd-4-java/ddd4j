package io.ddd4j.mq.listener;

import io.ddd4j.mq.config.MQProperties;

import io.ddd4j.kit.lang.StrKit;
import java.util.Objects;


/**
 * {@link EventListener} 端点物理命名约定（topic / queue / subject 等跨 Broker 复用）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class EndpointNaming {

    private EndpointNaming() {
    }

    /**
     * 解析分隔符，默认 {@code .}。
     */
    public static String resolveSeparator(ListenerDefinition definition) {
        if (Objects.nonNull(definition) && StrKit.hasText(definition.getSeparator())) {
            return definition.getSeparator();
        }
        return ".";
    }

    /**
     * 解析首个正向 tag（复合表达式取首个 include）。
     */
    public static String resolveTag(String tags) {
        return TagMatcher.findIncludes(tags).stream().findFirst().orElse(null);
    }

    /**
     * 构建物理 topic：namespace.topic[.tag]。
     */
    public static String physicalTopic(MQProperties properties, ListenerDefinition definition) {
        String sep = resolveSeparator(definition);
        String namespace = StrKit.hasText(definition.getNamespace())
                ? definition.getNamespace()
                : properties.getNamespace();
        String topic = definition.getTopic();
        String tag = resolveTag(definition.getTags());
        String base = namespace + sep + topic;
        return Objects.isNull(tag) ? base : base + sep + tag;
    }

    /**
     * 构建队列名：group.namespace.className.methodName（Rabbit / JMS 等）。
     */
    public static String queueName(ListenerDefinition definition) {
        String sep = resolveSeparator(definition);
        String group = definition.getGroup();
        String namespace = definition.getNamespace();
        String className = definition.getMethod().getDeclaringClass().getSimpleName();
        String methodName = definition.getMethod().getName();
        return group + sep + namespace + sep + className + sep + methodName;
    }

    /**
     * 构建端点 ID，保证在容器 registry 内唯一。
     */
    public static String endpointId(String brokerPrefix, ListenerDefinition definition) {
        return "ddd4j-" + brokerPrefix + "-" + definition.bindingName() + "-"
                + definition.getMethod().getDeclaringClass().getSimpleName() + "-"
                + definition.getMethod().getName();
    }

}
