package io.ddd4j.core.cqrs.readmodel;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** \u901a\u8fc7 <code>Collections.emptyList()</code>\u3001<code>dispatcher.size()</code>\u3001<code>dispatcher.dispatch()</code> \u9a8c\u8bc1 <code>TypedEventDispatcherParity</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class TypedEventDispatcherParityTest {

    /** \u9a8c\u8bc1<code>dispatcher.size()</code> \u7b49\u4e8e <code>0</code>\uff0c<code>dispatcher.dispatch("created", new CreatedEvent())</code>\u4e3a\u5047\uff0c<code>dispatcher.dispatch((TypedEvent) null)</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void emptyHandlersAndUnknownEventsAreIgnored() {
        TypedEventDispatcher dispatcher = new TypedEventDispatcher(Collections.emptyList());

        assertEquals(0, dispatcher.size());
        assertFalse(dispatcher.dispatch("created", new CreatedEvent()));
        assertFalse(dispatcher.dispatch(" ", new CreatedEvent()));
        assertFalse(dispatcher.dispatch("created", null));
        assertEquals(0, dispatcher.dispatchAll(Collections.emptyList()));
        assertThrows(NullPointerException.class, () -> dispatcher.dispatch((TypedEvent) null));
    }

    /** \u9a8c\u8bc1<code>dispatcher.size()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>dispatcher.dispatch(new CreatedEvent())</code>\u4e3a\u771f\uff0c<code>last.get()</code> \u7b49\u4e8e <code>1</code>\u3002 */
    @Test
    void duplicateEventTypeUsesTheLastHandler() {
        AtomicInteger first = new AtomicInteger();
        AtomicInteger last = new AtomicInteger();
        TypedEventDispatcher dispatcher = new TypedEventDispatcher(Arrays.asList(
                new CreatedHandler(first), new CreatedHandler(last)));

        assertEquals(1, dispatcher.size());
        assertTrue(dispatcher.dispatch(new CreatedEvent()));
        assertEquals(0, first.get());
        assertEquals(1, last.get());
    }

    /** \u9a8c\u8bc1<code>dispatcher.dispatchAll(Arrays.asList( new CreatedEvent(), new UnknownEvent(), new CreatedEvent()))</code> \u7b49\u4e8e <code>2</code>\uff0c<code>handled.get()</code> \u7b49\u4e8e <code>2</code>\u3002 */
    @Test
    void dispatchAllCountsOnlyHandledEvents() {
        AtomicInteger handled = new AtomicInteger();
        TypedEventDispatcher dispatcher = new TypedEventDispatcher(
                Collections.singletonList(new CreatedHandler(handled)));

        assertEquals(2, dispatcher.dispatchAll(Arrays.asList(
                new CreatedEvent(), new UnknownEvent(), new CreatedEvent())));
        assertEquals(2, handled.get());
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalArgumentException.class</code>\uff0c<code>dispatcher.dispatch("created", "wrong class")</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void invalidHandlersAndMismatchedEventClassAreRejected() {
        assertThrows(NullPointerException.class,
                () -> new TypedEventDispatcher(Collections.singletonList(null)));
        assertThrows(IllegalArgumentException.class,
                () -> new TypedEventDispatcher(Collections.singletonList(
                        new CreatedHandler(new AtomicInteger(), " "))));

        TypedEventDispatcher dispatcher = new TypedEventDispatcher(
                Collections.singletonList(new CreatedHandler(new AtomicInteger())));
        assertThrows(IllegalArgumentException.class,
                () -> dispatcher.dispatch("created", "wrong class"));
    }

    /** \u5b9e\u73b0 <code>TypedEvent</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>getEventType()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static final class CreatedEvent implements TypedEvent {
        /** {@inheritDoc} */
        @Override
        public String getEventType() {
            return "created";
        }
    }

    /** \u5b9e\u73b0 <code>TypedEvent</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>getEventType()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static final class UnknownEvent implements TypedEvent {
        /** {@inheritDoc} */
        @Override
        public String getEventType() {
            return "unknown";
        }
    }

    /** \u5b9e\u73b0 <code>TypedEventHandler</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>getEventType()</code>\u3001<code>getEventClass()</code>\u3001<code>handle()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static final class CreatedHandler implements TypedEventHandler<CreatedEvent> {
        /** \u5f53\u524d\u6d4b\u8bd5\u4e2d\u5df2\u7ecf\u4ea4\u7ed9\u5904\u7406\u5668\u7684\u8bb0\u5f55\u3002 */
        private final AtomicInteger handled;
        /** \u533a\u5206\u5f53\u524d\u6d4b\u8bd5\u4e8b\u4ef6\u7684\u903b\u8f91\u7c7b\u578b\u3002 */
        private final String eventType;

        /**
         * \u4f7f\u7528 <code>this()</code> \u521d\u59cb\u5316 <code>CreatedHandler</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param handled \u5f53\u524d\u6d4b\u8bd5\u4e2d\u5df2\u7ecf\u4ea4\u7ed9\u5904\u7406\u5668\u7684\u8bb0\u5f55
         */
        private CreatedHandler(AtomicInteger handled) {
            this(handled, "created");
        }

        /**
         * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>CreatedHandler</code> \u5b9e\u4f8b\u3002
         * @param handled \u5f53\u524d\u6d4b\u8bd5\u4e2d\u5df2\u7ecf\u4ea4\u7ed9\u5904\u7406\u5668\u7684\u8bb0\u5f55
         * @param eventType <code>eventType</code> \u5b57\u6bb5
         */
        private CreatedHandler(AtomicInteger handled, String eventType) {
            this.handled = handled;
            this.eventType = eventType;
        }

        /** {@inheritDoc} */
        @Override
        public String getEventType() {
            return eventType;
        }

        /** {@inheritDoc} */
        @Override
        public Class<CreatedEvent> getEventClass() {
            return CreatedEvent.class;
        }

        /** {@inheritDoc} */
        @Override
        public void handle(CreatedEvent event) {
            handled.incrementAndGet();
        }
    }
}
