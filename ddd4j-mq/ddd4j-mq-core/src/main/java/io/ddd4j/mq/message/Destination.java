package io.ddd4j.mq.message;

import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.naming.BindingNaming;
import lombok.Getter;


import io.ddd4j.kit.lang.StrKit;
import java.util.Objects;

/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Getter
public class Destination {

    private final String topic;
    private final String tag;
    private final String namespace;

    public Destination(String topic, String tag, String namespace) {
        this.topic = topic;
        this.tag = tag;
        this.namespace = namespace;
    }

    public static Destination from(MQEvent event) {
        return new Destination(event.getTopic(), event.getTag(), event.getNamespace());
    }

    public static Destination of(String topic, String tag) {
        return new Destination(topic, tag, null);
    }

    public static Destination of(String topic, String tag, String namespace) {
        return new Destination(topic, tag, namespace);
    }


    public String physicalDestination() {
        return StrKit.hasText(namespace) ? namespace + "." + topic : topic;
    }

    public String bindingOutName() {
        return BindingNaming.bindingName(topic, tag);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (Objects.isNull(o) || getClass() != o.getClass()) {
            return false;
        }
        Destination that = (Destination) o;
        return Objects.equals(topic, that.topic) && Objects.equals(tag, that.tag) && Objects.equals(namespace, that.namespace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(topic, tag, namespace);
    }
}
