/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.core.cqrs.readmodel;

import java.util.Objects;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TypedEventDispatcherTest {

    @Test
    void shouldDispatchMatchingEvent() {
        AtomicInteger handled = new AtomicInteger();
        TypedEventDispatcher dispatcher = new TypedEventDispatcher(Arrays.asList(
                new PersonCreatedHandler(handled)
        ));

        boolean dispatched = dispatcher.dispatch(new PersonCreatedEvent("p-1"));

        assertTrue(dispatched);
        assertEquals(1, handled.get());
    }

    @Test
    void shouldIgnoreUnknownEventType() {
        TypedEventDispatcher dispatcher = new TypedEventDispatcher(Arrays.asList());

        boolean dispatched = dispatcher.dispatch(new PersonCreatedEvent("p-1"));

        assertFalse(dispatched);
    }

    @Test
    void shouldRejectEventClassMismatch() {
        TypedEventDispatcher dispatcher = new TypedEventDispatcher(Arrays.asList(
                new PersonCreatedHandler(new AtomicInteger())
        ));

        assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatch("person.created", "wrong"));
    }static final class PersonCreatedEvent implements TypedEvent {
        private final String id;

        public PersonCreatedEvent(String id) {
            this.id = id;
        }
        public String id() { return id; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PersonCreatedEvent)) return false;
            PersonCreatedEvent other = (PersonCreatedEvent) o;
            return Objects.equals(this.id, other.id);
        }
        @Override
        public int hashCode() {
            return java.util.Objects.hash(id);
        }
        @Override
        public String toString() {
            return "PersonCreatedEvent{" + "id=" + id + "}";
        }
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
