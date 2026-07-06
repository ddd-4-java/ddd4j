package io.ddd4j.mq.consume;

import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.message.Message;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link EventPersistInterceptor} 持久化拦截器单测。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class EventPersistInterceptorTest {

    private static ConsumerContext context(MQEvent event) {
        return ConsumerContext.builder()
                .payload(event)
                .message(Message.of("raw"))
                .build();
    }

    private static MQEvent event(String tag) {
        MQEvent event = new MQEvent();
        event.setTopic("order.paid");
        event.setTag(tag);
        event.setMsgId("msg-" + tag);
        return event;
    }

    @Test
    void persistDisabledShouldSkipStorer() {
        MQProperties properties = new MQProperties();
        properties.setPersist(false);
        List<MQEvent> stored = new ArrayList<>();
        EventPersistInterceptor interceptor = new EventPersistInterceptor(properties, stored::add);

        int result = interceptor.preCheck(context(event("A")), Message.of("raw"));

        assertEquals(ConsumeTemplate.PRE_CONTINUE, result);
        assertEquals(0, stored.size());
    }

    @Test
    void missingStorerShouldNotInterruptConsumption() {
        MQProperties properties = new MQProperties();
        properties.setPersist(true);
        EventPersistInterceptor interceptor = new EventPersistInterceptor(properties, null);

        int result = interceptor.preCheck(context(event("A")), Message.of("raw"));

        assertEquals(ConsumeTemplate.PRE_CONTINUE, result);
    }

    @Test
    void persistEnabledShouldStoreResolvedMqEvent() {
        MQProperties properties = new MQProperties();
        properties.setPersist(true);
        List<MQEvent> stored = new ArrayList<>();
        MQEvent event = event("A");
        EventPersistInterceptor interceptor = new EventPersistInterceptor(properties, stored::add);

        int result = interceptor.preCheck(context(event), Message.of("raw"));

        assertEquals(ConsumeTemplate.PRE_CONTINUE, result);
        assertEquals(List.of(event), stored);
    }

    @Test
    void storerExceptionShouldNotInterruptConsumption() {
        MQProperties properties = new MQProperties();
        properties.setPersist(true);
        EventPersistInterceptor interceptor = new EventPersistInterceptor(properties, event -> {
            throw new IllegalStateException("store failed");
        });

        int result = interceptor.preCheck(context(event("A")), Message.of("raw"));

        assertEquals(ConsumeTemplate.PRE_CONTINUE, result);
    }
}
