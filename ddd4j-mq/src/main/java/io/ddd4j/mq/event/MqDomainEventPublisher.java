package io.ddd4j.mq.event;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.ddd.event.EntityId;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.serialization.MQEventSerialization;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * 跨进程领域事件转发器（回填自 3.0.x c36ad164，适配 1.0.x 契约）。
 *
 * <p>实现 {@link DomainEventPublisher} 端口，将进程内领域事件序列化为
 * JSON 载荷并经 {@link MQEventPublisher}（含 MQEventPublisherContract SPI）
 * 发布到 MQ。topic 取事件类型简单名，tag 固定 {@value #DOMAIN_EVENT_TAG}
 * 便于消费者统一批量订阅。
 */
@Slf4j
public class MqDomainEventPublisher implements DomainEventPublisher {

    /** 领域事件统一 tag，便于消费者批量订阅。 */
    public static final String DOMAIN_EVENT_TAG = "domain-event";

    private final MQEventSerialization serialization;
    private final MQEventPublisher mqEventPublisher;

    /**
     * @param serialization    MQ 事件序列化器
     * @param mqEventPublisher MQ 发布端口
     */
    public MqDomainEventPublisher(MQEventSerialization serialization, MQEventPublisher mqEventPublisher) {
        this.serialization = Objects.requireNonNull(serialization, "serialization must not be null");
        this.mqEventPublisher = Objects.requireNonNull(mqEventPublisher, "mqEventPublisher must not be null");
    }

    @Override
    public <ID extends EntityId> void publish(DomainEvent<ID> event) {
        if (Objects.isNull(event)) {
            return;
        }
        DomainEventCarrier carrier = toCarrier(event);
        log.info("Publishing domain event to MQ: type={}, eventId={}", event.getEventType().asString(), event.getEventId());
        mqEventPublisher.publish(carrier);
    }

    /**
     * 将领域事件转换为 {@link DomainEventCarrier}。
     *
     * @param event 领域事件
     * @return MQ 载体事件
     */
    DomainEventCarrier toCarrier(DomainEvent<?> event) {
        String payload = serialization.serialize(event);
        DomainEventCarrier carrier = new DomainEventCarrier(event.getClass().getName(), payload);
        // topic = eventType 简单名（如 "OrderCreated"）
        carrier.setTopic(event.getEventType().asString());
        // tag = 固定 "domain-event"
        carrier.setTag(DOMAIN_EVENT_TAG);
        // 消息 ID 复用领域事件 ID
        if (Objects.nonNull(event.getEventId())) {
            carrier.setMsgId(event.getEventId().asString());
        }
        return carrier;
    }
}
