package io.ddd4j.boot.cmpt.tdmq.publisher;

import io.ddd4j.boot.cmpt.tdmq.client.TdmqClient;
import io.ddd4j.boot.core.contract.MQEvent;
import io.ddd4j.boot.core.utils.JsonKit;
import io.ddd4j.boot.mq.config.Ddd4jMQProperties;
import io.ddd4j.boot.mq.contract.MQDestination;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 基于 {@link TdmqClient} 的领域事件发布实现（占位骨架）。
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
        if (!StringUtils.hasText(event.getTopic())) {
            event.setTopic(properties.getDefaultTopic());
        }
        if (!StringUtils.hasText(event.getNamespace())) {
            event.setNamespace(properties.getNamespace());
        }
        if (event.getMsgId() == null) {
            event.setMsgId(String.valueOf(System.currentTimeMillis()));
        }

        // 逻辑块：序列化并委托 TDMQ 客户端
        String topic = destination.physicalDestination();
        String tag = StringUtils.hasText(destination.tag()) ? destination.tag() : event.getTag();
        String payload = JsonKit.toJson(event);
        tdmqClient.publish(topic, tag, payload.getBytes(StandardCharsets.UTF_8));
        log.debug("Published TDMQ event (placeholder), topic={}, tag={}, msgId={}", topic, tag, event.getMsgId());
    }
}
