package io.ddd4j.mq.ons.publisher;

import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.Producer;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.message.Destination;
import io.ddd4j.mq.message.DestinationResolver;
import io.ddd4j.mq.ons.spi.OnsMQProperties;
import io.ddd4j.mq.event.MQEventPublisher;
import io.ddd4j.mq.serialization.EventSerialization;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 阿里云 ONS 事件发布器（纯 Java，原生 ons-client）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class OnsMQEventPublisher implements MQEventPublisher {

    private final Producer producer;
    private final OnsMQProperties properties;
    private final MQProperties mqProperties;
    private final EventSerialization serialization;

    public OnsMQEventPublisher(Producer producer, OnsMQProperties properties,
                               MQProperties mqProperties, EventSerialization serialization) {
        this.producer = Objects.requireNonNull(producer, "producer");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
    }

    @Override
    public <T extends MQEvent> void publish(T event, Destination destination) {
        try {
            DestinationResolver.fillDefaults(event, mqProperties);
            String topic = StrKit.hasText(destination.getTopic())
                    ? destination.getTopic()
                    : (StrKit.hasText(event.getTopic())
                            ? event.getTopic()
                            : (StrKit.hasText(properties.getTopic()) ? properties.getTopic() : "ddd4j.default.topic"));
            String tag = StrKit.hasText(destination.getTag()) ? destination.getTag() : event.getTag();
            Message msg = new Message(topic, tag, event.getMsgId(),
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
