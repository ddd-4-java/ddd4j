package io.ddd4j.mq.tdmq.publisher;

import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.tdmq.client.TdmqClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.ddd4j.kit.lang.StrKit;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 基于 {@link TdmqClient} 的领域事件发布实现（占位骨架）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@RequiredArgsConstructor
public class TdmqMQEventPublisher implements MQEventPublisher {

    private final TdmqClient tdmqClient;
    private final Ddd4jMQProperties properties;

    @Override
    public <T extends MQEvent> void publish(T event, MQDestination destination) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(tdmqClient, "tdmqClient");

        // 逻辑块：补齐事件元数据
        if (!StrKit.isNotBlank(event.getTopic())) {
            event.setTopic(properties.getDefaultTopic());
        }
        if (!StrKit.isNotBlank(event.getNamespace())) {
            event.setNamespace(properties.getNamespace());
        }
        if (event.getMsgId() == null) {
            event.setMsgId(String.valueOf(System.currentTimeMillis()));
        }

        // 逻辑块：序列化并委托 TDMQ 客户端
        String topic = destination.physicalDestination();
        String tag = StrKit.isNotBlank(destination.getTag()) ? destination.getTag() : event.getTag();
        String payload = JsonKit.toJson(event);
        tdmqClient.publish(topic, tag, payload.getBytes(StandardCharsets.UTF_8));
        log.debug("Published TDMQ event (placeholder), topic={}, tag={}, msgId={}", topic, tag, event.getMsgId());
    }
}
