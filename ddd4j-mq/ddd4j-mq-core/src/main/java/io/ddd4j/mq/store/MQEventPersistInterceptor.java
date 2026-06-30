package io.ddd4j.mq.store;

import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.mq.ack.MQConsumeTemplates;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumeInterceptor;
import io.ddd4j.mq.consume.MQConsumerContext;
import io.ddd4j.mq.contract.MQMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MQ 事件持久化拦截器。
 *
 * <p>对齐旧库语义：仅在 {@code ddd4j.mq.persist=true} 且业务注册了
 * {@link MQEventStorer} 时工作；持久化失败只记录日志，不阻断后续消费。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class MQEventPersistInterceptor implements MQConsumeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(MQEventPersistInterceptor.class);

    private final Ddd4jMQProperties properties;
    @SuppressWarnings("rawtypes")
    private final MQEventStorer storer;

    @SuppressWarnings("rawtypes")
    public MQEventPersistInterceptor(Ddd4jMQProperties properties, MQEventStorer storer) {
        this.properties = properties;
        this.storer = storer;
    }

    @Override
    public int order() {
        return Integer.MIN_VALUE + 100;
    }

    @Override
    public int preCheck(MQConsumerContext context, MQMessage<?> message) {
        if (properties == null || !properties.isPersist() || storer == null) {
            return MQConsumeTemplates.PRE_CONTINUE;
        }
        Object payload = context == null ? null : context.getPayload();
        if (!(payload instanceof MQEvent) && message != null) {
            payload = message.getPayload();
        }
        if (payload instanceof MQEvent event) {
            persist(event);
        }
        return MQConsumeTemplates.PRE_CONTINUE;
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
