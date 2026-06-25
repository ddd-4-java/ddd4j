package io.ddd4j.mq.contract;

import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.mq.registry.MQBindingNaming;
import org.springframework.util.StringUtils;

/**
 * MQ 目的地值对象：namespace / topic / tag 语义层抽象。
 */
public record MQDestination(String topic, String tag, String namespace) {

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
}
