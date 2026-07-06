package io.ddd4j.mq.consume;

import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.message.Message;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * MQ 事件持久化拦截器。
 *
 * <p>对齐旧库语义：仅在 {@code ddd4j.mq.persist=true} 且业务注册了
 * {@link EventStorer} 时工作；持久化失败只记录日志，不阻断后续消费。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class EventPersistInterceptor implements ConsumerInterceptor {

    private final MQProperties properties;
    @SuppressWarnings("rawtypes")
    private final EventStorer storer;

    @SuppressWarnings("rawtypes")
    public EventPersistInterceptor(MQProperties properties, EventStorer storer) {
        this.properties = properties;
        this.storer = storer;
    }

    @Override
    public int order() {
        return Integer.MIN_VALUE + 100;
    }

    @Override
    public int preCheck(ConsumerContext context, Message<?> message) {
        if (Objects.isNull(properties) || !properties.isPersist() || Objects.isNull(storer)) {
            return ConsumeTemplate.PRE_CONTINUE;
        }
        Object payload = Objects.isNull(context) ? null : context.getPayload();
        if (!(payload instanceof MQEvent) && Objects.nonNull(message)) {
            payload = message.getPayload();
        }
        if (payload instanceof MQEvent event) {
            persist(event);
        }
        return ConsumeTemplate.PRE_CONTINUE;
    }

    @SuppressWarnings("unchecked")
    private void persist(MQEvent event) {
        try {
            storer.store(event);
        } catch (Exception ex) {
            log.error("Persist MQ event failed: topic={}, tag={}, msgId={}",
                    event.getTopic(), event.getTag(), event.getMsgId(), ex);
        }
    }
}
