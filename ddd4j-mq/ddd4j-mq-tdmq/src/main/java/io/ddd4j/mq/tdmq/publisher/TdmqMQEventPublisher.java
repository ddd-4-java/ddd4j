package io.ddd4j.mq.tdmq.publisher;

import io.ddd4j.core.domain.event.MQEvent;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.serialization.MQEventSerialization;
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
public class TdmqMQEventPublisher implements MQEventPublisher {

    private final TdmqClient tdmqClient;
    private final Ddd4jMQProperties properties;
    private final MQEventSerialization serialization;

    public TdmqMQEventPublisher(TdmqClient tdmqClient,
                                Ddd4jMQProperties properties,
                                MQEventSerialization serialization) {
        this.tdmqClient = Objects.requireNonNull(tdmqClient, "tdmqClient");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
    }

    @Override
    public <T extends MQEvent> void publish(T event, MQDestination destination) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(destination, "destination");
        enrichEvent(event);
        String topic = destination.physicalDestination();
        String tag = StrKit.isNotBlank(destination.getTag()) ? destination.getTag() : event.getTag();
        String payload = serialization.serialize(event);
        tdmqClient.publish(topic, tag, payload.getBytes(StandardCharsets.UTF_8));
        log.debug("Published TDMQ event, topic={}, tag={}, msgId={}", topic, tag, event.getMsgId());
    }

    private void enrichEvent(MQEvent event) {
        if (StrKit.isBlank(event.getTopic())) {
            event.setTopic(properties.getDefaultTopic());
        }
        if (StrKit.isBlank(event.getNamespace())) {
            event.setNamespace(properties.getNamespace());
        }
        if (Objects.isNull(event.getMsgId())) {
            event.setMsgId(String.valueOf(System.currentTimeMillis()));
        }
    }
}
