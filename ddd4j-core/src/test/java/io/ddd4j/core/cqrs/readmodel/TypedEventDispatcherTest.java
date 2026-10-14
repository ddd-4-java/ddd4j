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

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

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

    final static class PersonCreatedEvent implements TypedEvent {

        private static final long serialVersionUID = 0L;

        private final String id;

        @Override
        public String getEventType() {
            return "person.created";
        }

        @JsonCreator()
        PersonCreatedEvent(@JsonProperty("id") String id) {
            this.id = id;
        }

        @JsonProperty("id")
        public String id() {
            return id;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            PersonCreatedEvent other = (PersonCreatedEvent) obj;
            return Objects.equals(this.id, other.id);
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(id);
            return result;
        }

        @Override
        public String toString() {
            return "PersonCreatedEvent[id=" + id + "]";
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
