package io.ddd4j.mq.contract;

import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.mq.registry.MQBindingNaming;
import lombok.Getter;


import java.util.Objects;

@Getter
/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class MQDestination {

    private final String topic;
    private final String tag;
    private final String namespace;

    public MQDestination(String topic, String tag, String namespace) {
        this.topic = topic;
        this.tag = tag;
        this.namespace = namespace;
    }

    public static MQDestination from(MQEvent event) {
        return new MQDestination(event.getTopic(), event.getTag(), event.getNamespace());
    }

    public static MQDestination of(String topic, String tag) {
        return new MQDestination(topic, tag, null);
    }

    public static MQDestination of(String topic, String tag, String namespace) {
        return new MQDestination(topic, tag, namespace);
    }

    public String physicalDestination() {
        return hasText(namespace) ? namespace + "." + topic : topic;
    }

    public String bindingOutName() {
        return MQBindingNaming.bindingName(topic, tag);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MQDestination that = (MQDestination) o;
        return Objects.equals(topic, that.topic) && Objects.equals(tag, that.tag) && Objects.equals(namespace, that.namespace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(topic, tag, namespace);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
