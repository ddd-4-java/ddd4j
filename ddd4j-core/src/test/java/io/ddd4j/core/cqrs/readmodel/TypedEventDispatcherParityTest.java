package io.ddd4j.core.cqrs.readmodel;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypedEventDispatcherParityTest {

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

    @Test
    void dispatchAllCountsOnlyHandledEvents() {
        AtomicInteger handled = new AtomicInteger();
        TypedEventDispatcher dispatcher = new TypedEventDispatcher(
                Collections.singletonList(new CreatedHandler(handled)));

        assertEquals(2, dispatcher.dispatchAll(Arrays.asList(
                new CreatedEvent(), new UnknownEvent(), new CreatedEvent())));
        assertEquals(2, handled.get());
    }

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

    private static final class CreatedEvent implements TypedEvent {
        @Override
        public String getEventType() {
            return "created";
        }
    }

    private static final class UnknownEvent implements TypedEvent {
        @Override
        public String getEventType() {
            return "unknown";
        }
    }

    private static final class CreatedHandler implements TypedEventHandler<CreatedEvent> {
        private final AtomicInteger handled;
        private final String eventType;

        private CreatedHandler(AtomicInteger handled) {
            this(handled, "created");
        }

        private CreatedHandler(AtomicInteger handled, String eventType) {
            this.handled = handled;
            this.eventType = eventType;
        }

        @Override
        public String getEventType() {
            return eventType;
        }

        @Override
        public Class<CreatedEvent> getEventClass() {
            return CreatedEvent.class;
        }

        @Override
        public void handle(CreatedEvent event) {
            handled.incrementAndGet();
        }
    }
}
