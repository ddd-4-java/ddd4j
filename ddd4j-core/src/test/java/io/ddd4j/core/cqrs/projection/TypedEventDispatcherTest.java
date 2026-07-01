package io.ddd4j.core.cqrs.projection;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypedEventDispatcherTest {

    @Test
    void shouldDispatchMatchingEvent() {
        AtomicInteger handled = new AtomicInteger();
        TypedEventDispatcher dispatcher = new TypedEventDispatcher(List.of(
                new PersonCreatedHandler(handled)
        ));

        boolean dispatched = dispatcher.dispatch(new PersonCreatedEvent("p-1"));

        assertTrue(dispatched);
        assertEquals(1, handled.get());
    }

    @Test
    void shouldIgnoreUnknownEventType() {
        TypedEventDispatcher dispatcher = new TypedEventDispatcher(List.of());

        boolean dispatched = dispatcher.dispatch(new PersonCreatedEvent("p-1"));

        assertFalse(dispatched);
    }

    @Test
    void shouldRejectEventClassMismatch() {
        TypedEventDispatcher dispatcher = new TypedEventDispatcher(List.of(
                new PersonCreatedHandler(new AtomicInteger())
        ));

        assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatch("person.created", "wrong"));
    }

    record PersonCreatedEvent(String id) implements TypedEvent {

        @Override
        public String getEventType() {
            return "person.created";
        }
    }

    static class PersonCreatedHandler implements TypedEventHandler<PersonCreatedEvent> {

        private final AtomicInteger handled;

        PersonCreatedHandler(AtomicInteger handled) {
            this.handled = handled;
        }

        @Override
        public String getEventType() {
            return "person.created";
        }

        @Override
        public Class<PersonCreatedEvent> getEventClass() {
            return PersonCreatedEvent.class;
        }

        @Override
        public void handle(PersonCreatedEvent event) {
            handled.incrementAndGet();
        }
    }
}
