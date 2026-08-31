package io.ddd4j.mq.contract;

import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.mq.registry.MQBindingNaming;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * MQ 目的地值对象：namespace / topic / tag 语义层抽象。
 */
public final class MQDestination {

    private final String topic;
    private final String tag;
    private final String namespace;

    public MQDestination(String topic, String tag, String namespace) {
        this.topic = topic;
        this.tag = tag;
        this.namespace = namespace;
    }

    public String topic() {
        return topic;
    }

    public String tag() {
        return tag;
    }

    public String namespace() {
        return namespace;
    }

    /**
     * 从已填充的 {@link MQEvent} 构建目的地。
     */
    public static MQDestination from(MQEvent event) {
        return new MQDestination(event.getTopic(), event.getTag(), event.getNamespace());
    }

    /**
     * 按 topic、tag 构建目的地（无 namespace）。
     */
    public static MQDestination of(String topic, String tag) {
        return new MQDestination(topic, tag, null);
    }

    /**
     * 按 topic、tag、namespace 构建目的地。
     */
    public static MQDestination of(String topic, String tag, String namespace) {
        return new MQDestination(topic, tag, namespace);
    }

    /**
     * 物理 destination（namespace.topic），供 Binder / cmpt 使用。
     */
    public String physicalDestination() {
        if (!StringUtils.hasText(namespace)) {
            return topic;
        }
        return namespace + "." + topic;
    }

    /**
     * 生成 Spring Cloud Stream 出站 binding 名（camelCase）。
     */
    public String bindingOutName() {
        return MQBindingNaming.bindingName(topic, tag);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MQDestination)) {
            return false;
        }
        MQDestination that = (MQDestination) other;
        return Objects.equals(topic, that.topic)
                && Objects.equals(tag, that.tag)
                && Objects.equals(namespace, that.namespace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(topic, tag, namespace);
    }

    @Override
    public String toString() {
        return "MQDestination[topic=" + topic + ", tag=" + tag + ", namespace=" + namespace + "]";
    }
}
