package io.ddd4j.mq.store;

import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.mq.ack.MQConsumeTemplates;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerContext;
import io.ddd4j.mq.contract.MQMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link MQEventPersistInterceptor} 持久化拦截器单测。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class MQEventPersistInterceptorTest {

    @Test
    void persistDisabledShouldSkipStorer() {
        Ddd4jMQProperties properties = new Ddd4jMQProperties();
        properties.setPersist(false);
        List<MQEvent> stored = new ArrayList<>();
        MQEventPersistInterceptor interceptor = new MQEventPersistInterceptor(properties, stored::add);

        int result = interceptor.preCheck(context(event("A")), MQMessage.of("raw"));

        assertEquals(MQConsumeTemplates.PRE_CONTINUE, result);
        assertEquals(0, stored.size());
    }

    @Test
    void missingStorerShouldNotInterruptConsumption() {
        Ddd4jMQProperties properties = new Ddd4jMQProperties();
        properties.setPersist(true);
        MQEventPersistInterceptor interceptor = new MQEventPersistInterceptor(properties, null);

        int result = interceptor.preCheck(context(event("A")), MQMessage.of("raw"));

        assertEquals(MQConsumeTemplates.PRE_CONTINUE, result);
    }

    @Test
    void persistEnabledShouldStoreResolvedMqEvent() {
        Ddd4jMQProperties properties = new Ddd4jMQProperties();
        properties.setPersist(true);
        List<MQEvent> stored = new ArrayList<>();
        MQEvent event = event("A");
        MQEventPersistInterceptor interceptor = new MQEventPersistInterceptor(properties, stored::add);

        int result = interceptor.preCheck(context(event), MQMessage.of("raw"));

        assertEquals(MQConsumeTemplates.PRE_CONTINUE, result);
        assertEquals(List.of(event), stored);
    }

    @Test
    void storerExceptionShouldNotInterruptConsumption() {
        Ddd4jMQProperties properties = new Ddd4jMQProperties();
        properties.setPersist(true);
        MQEventPersistInterceptor interceptor = new MQEventPersistInterceptor(properties, event -> {
            throw new IllegalStateException("store failed");
        });

        int result = interceptor.preCheck(context(event("A")), MQMessage.of("raw"));

        assertEquals(MQConsumeTemplates.PRE_CONTINUE, result);
    }

    private static MQConsumerContext context(MQEvent event) {
        return MQConsumerContext.builder()
                .payload(event)
                .message(MQMessage.of("raw"))
                .build();
    }

    private static MQEvent event(String tag) {
        MQEvent event = new MQEvent();
        event.setTopic("order.paid");
        event.setTag(tag);
        event.setMsgId("msg-" + tag);
        return event;
    }
}
