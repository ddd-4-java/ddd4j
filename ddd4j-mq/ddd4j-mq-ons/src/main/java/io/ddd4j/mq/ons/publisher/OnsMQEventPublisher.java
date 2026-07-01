package io.ddd4j.mq.ons.publisher;

import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.Producer;
import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.ons.spi.OnsMQProperties;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.serialization.MQEventSerialization;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Alibaba ONS event publisher (pure Java, native ons-client).
 */
public class OnsMQEventPublisher implements MQEventPublisher {

    private final Producer producer;
    private final OnsMQProperties properties;
    private final Ddd4jMQProperties mqProperties;
    private final MQEventSerialization serialization;

    public OnsMQEventPublisher(Producer producer, OnsMQProperties properties,
                               Ddd4jMQProperties mqProperties, MQEventSerialization serialization) {
        this.producer = Objects.requireNonNull(producer, "producer");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
    }

    private static String firstText(String... values) {
        if (Objects.isNull(values)) {
            return null;
        }
        for (String v : values) {
            if (Objects.nonNull(v) && !io.ddd4j.kit.lang.StrKit.isBlank(v)) {
                return v;
            }
        }
        return null;
    }

    @Override
    public <T extends MQEvent> void publish(T event, MQDestination destination) {
        try {
            String topic = firstText(destination.getTopic(), event.getTopic(), properties.getTopic(), "ddd4j.default.topic");
            String tag = firstText(destination.getTag(), event.getTag());
            Message msg = new Message(topic, tag, Objects.isNull(event.getMsgId()) ? event.getMsgId() : event.getMsgId(),
                    serialization.serialize(event).toString().getBytes(StandardCharsets.UTF_8));
            msg.setKey(event.getMsgId());
            if (Objects.nonNull(event.getTenantId())) {
                msg.putUserProperties("tenantId", event.getTenantId());
            }
            producer.send(msg);
        } catch (Exception ex) {
            throw new IllegalStateException("Publish ONS event failed", ex);
        }
    }
}
