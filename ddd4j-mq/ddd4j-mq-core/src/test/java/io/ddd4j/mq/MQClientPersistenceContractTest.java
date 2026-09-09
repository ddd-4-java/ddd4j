package io.ddd4j.mq;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.listener.MQListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MQClientPersistenceContractTest {

    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    @Test
    void persistShouldFailBeforeBusinessHandlerWhenStorerIsMissing() throws Throwable {
        AtomicBoolean invoked = new AtomicBoolean();
        MQClient client = client();
        MQProperties properties = properties();
        client.init(Collections.<MQListener>emptyList(), properties, serialization(), null);

        assertThrows(IllegalStateException.class,
                () -> client.consume(listener(invoked), event(), null));
        assertFalse(invoked.get());
    }

    @Test
    void persistShouldFailBeforeBusinessHandlerWhenStoreFails() throws Throwable {
        AtomicBoolean invoked = new AtomicBoolean();
        MQClient client = client();
        MQProperties properties = properties();
        client.init(Collections.<MQListener>emptyList(), properties, serialization(), event -> {
            throw new IllegalStateException("store unavailable");
        });

        assertThrows(IllegalStateException.class,
                () -> client.consume(listener(invoked), event(), null));
        assertFalse(invoked.get());
    }

    private MQClient client() {
        return new MQClient() {
            @Override public String impl() { return "test"; }
            @Override public Consumer<MQEvent> initProducer(MQProperties properties) { return null; }
            @Override public boolean initConsumer(MQListener listener, MQProperties properties) { return false; }
        };
    }

    private MQProperties properties() {
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setBroker("test");
        properties.setPersist(true);
        return properties;
    }

    private MQEventSerialization serialization() {
        return new MQEventSerialization() {
            @Override @SuppressWarnings("unchecked") public <T> T serialize(Object event) { return (T) "{}"; }
            @Override public <S, T> T deserialize(S value, Class<T> type) { return null; }
        };
    }

    private MQListener listener(AtomicBoolean invoked) throws Exception {
        Handler handler = new Handler(invoked);
        Method method = Handler.class.getMethod("handle", MQEvent.class);
        return MQListener.builder().bean(handler).method(method).topic("orders").tags("*")
                .supports(Collections.singletonList("*")).group("test").namespace("").separator(".").build();
    }

    private MQEvent event() {
        MQEvent event = new MQEvent();
        event.setTopic("orders");
        return event;
    }

    public static final class Handler {
        private final AtomicBoolean invoked;
        Handler(AtomicBoolean invoked) { this.invoked = invoked; }
        public void handle(MQEvent event) { invoked.set(true); }
    }
}
