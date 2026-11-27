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

/** \u9a8c\u8bc1 <code>MQClientPersistenceContract</code> \u5728 <code>persist</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class MQClientPersistenceContractTest {

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u6e05\u9664<code>BaseContext</code>\u3002 */
    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    /**
     * \u9a8c\u8bc1<code>client.consume(listener(invoked), event(), null)</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>invoked.get()</code>\u4e3a\u5047\u3002
     * @throws Throwable \u8bfb\u53d6\u6d4b\u8bd5\u6570\u636e\u6216\u7b49\u5f85\u6d4b\u8bd5\u7ed3\u679c\u5931\u8d25
     */
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

    /**
     * \u9a8c\u8bc1<code>client.consume(listener(invoked), event(), null)</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>invoked.get()</code>\u4e3a\u5047\u3002
     * @throws Throwable \u8bfb\u53d6\u6d4b\u8bd5\u6570\u636e\u6216\u7b49\u5f85\u6d4b\u8bd5\u7ed3\u679c\u5931\u8d25
     */
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

    /**
     * \u6784\u9020 <code>MQClient</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>MQClient</code> \u7ed3\u679c
     */
    private MQClient client() {
        return new MQClient() {
            @Override
            public String impl() {
                return "test";
            }

            @Override
            public Consumer<MQEvent> initProducer(MQProperties properties) {
                return null;
            }

            @Override
            public boolean initConsumer(MQListener listener, MQProperties properties) {
                return false;
            }
        };
    }

    /**
     * \u8bfb\u53d6 <code>properties</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>properties</code>
     */
    private MQProperties properties() {
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setBroker("test");
        properties.setPersist(true);
        return properties;
    }

    /**
     * \u6784\u9020 <code>MQEventSerialization</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>MQEventSerialization</code> \u7ed3\u679c
     */
    private MQEventSerialization serialization() {
        return new MQEventSerialization() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T serialize(Object event) {
                return (T) "{}";
            }

            @Override
            public <S, T> T deserialize(S value, Class<T> type) {
                return null;
            }
        };
    }

    /**
     * \u8c03\u7528 <code>Handler.class.getMethod()</code>\u3001<code>MQListener.builder()</code>\u3001<code>Collections.singletonList()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param invoked \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>AtomicBoolean</code> \u53c2\u6570 <code>invoked</code>
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>MQListener</code> \u7ed3\u679c
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    private MQListener listener(AtomicBoolean invoked) throws Exception {
        Handler handler = new Handler(invoked);
        Method method = Handler.class.getMethod("handle", MQEvent.class);
        return MQListener.builder().bean(handler).method(method).topic("orders").tags("*")
                .supports(Collections.singletonList("*")).group("test").namespace("").separator(".").build();
    }

    /**
     * \u8bfb\u53d6 <code>event</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>event</code>
     */
    private MQEvent event() {
        MQEvent event = new MQEvent();
        event.setTopic("orders");
        return event;
    }

    /** \u5c06 <code>invoked</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    public static final class Handler {
        /** \u8bb0\u5f55\u53d7\u6d4b\u6267\u884c\u5668\u6216\u76d1\u542c\u5668\u662f\u5426\u88ab\u8c03\u7528\u3002 */
        private final AtomicBoolean invoked;

        /**
         * \u8bbe\u7f6e <code>invoked</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>Handler</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param invoked \u7528\u4e8e\u521d\u59cb\u5316 <code>Handler</code> \u7684 <code>AtomicBoolean</code> \u503c
         */
        Handler(AtomicBoolean invoked) {
            this.invoked = invoked;
        }

        /**
         * \u8c03\u7528 <code>invoked.set()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
        public void handle(MQEvent event) {
            invoked.set(true);
        }
    }
}
