package io.ddd4j.mq.tdmq.publisher;

import io.ddd4j.core.event.MQEvent;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.message.Destination;
import io.ddd4j.mq.message.DestinationResolver;
import io.ddd4j.mq.publish.EventPublisher;
import io.ddd4j.mq.serialization.EventSerialization;
import io.ddd4j.mq.tdmq.client.TdmqClient;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 基于 {@link TdmqClient} 的领域事件发布实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class TdmqEventPublisher implements EventPublisher {

    private final TdmqClient tdmqClient;
    private final MQProperties properties;
    private final EventSerialization serialization;

    public TdmqEventPublisher(TdmqClient tdmqClient,
                                MQProperties properties,
                                EventSerialization serialization) {
        this.tdmqClient = Objects.requireNonNull(tdmqClient, "tdmqClient");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
    }

    @Override
    public <T extends MQEvent> void publish(T event, Destination destination) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(destination, "destination");
        DestinationResolver.fillDefaults(event, properties);
        String topic = destination.physicalDestination();
        String tag = StrKit.isNotBlank(destination.getTag()) ? destination.getTag() : event.getTag();
        String payload = serialization.serialize(event);
        tdmqClient.publish(topic, tag, payload.getBytes(StandardCharsets.UTF_8));
        log.debug("Published TDMQ event, topic={}, tag={}, msgId={}", topic, tag, event.getMsgId());
    }
}
