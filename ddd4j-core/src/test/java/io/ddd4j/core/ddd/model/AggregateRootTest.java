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
package io.ddd4j.core.ddd.model;

import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.EventHandler;
import io.ddd4j.core.ddd.event.StringEntityId;
import io.ddd4j.core.ddd.event.StringEntityType;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AggregateRootTest {
    @Test
    void shouldCollectNewEventsButNotReplayHistory() {
        AppliedAggregate aggregate = new AppliedAggregate();
        TestEvent created = new TestEvent();
        aggregate.record(created);
        assertEquals(1, aggregate.pullDomainEvents().size());
        aggregate.loadFromHistory(Collections.<DomainEvent<?>>singletonList(new TestEvent()));
        assertEquals(0, aggregate.pullDomainEvents().size());
    }
    @Test
    void shouldDispatchApplyAndReplayToEventHandler() {
        HandlingAggregate aggregate = new HandlingAggregate();
        aggregate.record(new TestEvent());
        assertEquals(1, aggregate.handled);
        aggregate.loadFromHistory(Collections.<DomainEvent<?>>singletonList(new TestEvent()));
        assertEquals(2, aggregate.handled);
        assertThrows(IllegalStateException.class, () -> new AggregateRoot<StringEntityId>() {
            @Override public StringEntityId id() { return new StringEntityId("missing"); }
        }.apply(new TestEvent()));
    }
    @Test
    void shouldExposeDomainIdentityAndCompareEntitiesById() {
        IdentityAggregate first = new IdentityAggregate(new StringEntityId("same"));
        IdentityAggregate second = new IdentityAggregate(new StringEntityId("same"));
        assertEquals(first.id(), second.id());
        assertEquals(true, first.sameIdentityAs(second));
    }
    @Test
    void shouldPersistThroughRegisteredRepository() {
        PersistentAggregate aggregate = new PersistentAggregate(new StringEntityId("persisted"));
        RecordingRepository repository = new RecordingRepository();
        RepositoryRegistry.register(PersistentAggregate.class, repository);
        assertEquals(aggregate, aggregate.save());
        aggregate.update();
        aggregate.delete();
        assertEquals(2, repository.saves);
        assertEquals(1, repository.deletes);
        RepositoryRegistry.unregister(PersistentAggregate.class);
    }
    private static final class AppliedAggregate extends AggregateRoot<StringEntityId> {
        void record(TestEvent event) { apply(event); }
        @EventHandler private void on(TestEvent event) { }
        @Override public StringEntityId id() { return new StringEntityId("order-1"); }
    }
    private static final class TestEvent extends DomainEvent<TestRootId> {
        TestEvent() { super(new EntityIdPath(new TestRootId("order-1"))); }
    }
    private static final class TestRootId implements AggregateRootId {
        private static final EntityType TYPE = new StringEntityType("Order");
        private final String value;
        private TestRootId(String value) { this.value = value; }
        @Override public EntityType getType() { return TYPE; }
        @Override public String asString() { return value; }
        @Override public String asTypedString() { return TYPE.asString() + ":" + value; }
    }
    private static final class HandlingAggregate extends AggregateRoot<StringEntityId> {
        private int handled;
        void record(TestEvent event) { apply(event); }
        @EventHandler private void on(TestEvent event) { handled++; }
        @Override public StringEntityId id() { return new StringEntityId("order-1"); }
    }
    private static final class IdentityAggregate extends AggregateRoot<StringEntityId> {
        private final StringEntityId id;
        private IdentityAggregate(StringEntityId id) { this.id = id; }
        @Override public StringEntityId id() { return id; }
    }
    private static final class PersistentAggregate extends AggregateRoot<StringEntityId> {
        private final StringEntityId id; private PersistentAggregate(StringEntityId id) { this.id = id; }
        @Override public StringEntityId id() { return id; }
    }
    private static final class RecordingRepository implements Repository<PersistentAggregate, StringEntityId> {
        private int saves; private int deletes;
        @Override public Optional<PersistentAggregate> findById(StringEntityId id) { return Optional.empty(); }
        @Override public PersistentAggregate save(PersistentAggregate aggregate) { saves++; return aggregate; }
        @Override public void deleteById(StringEntityId id) { deletes++; }
    }
    @Test
    void shouldBufferPullAndClearDomainEvents() {
        TestAggregate aggregate = new TestAggregate("order-1");

        aggregate.rename("new-name");

        assertTrue(aggregate.hasDomainEvents());
        assertEquals(1, aggregate.domainEvents().size());

        List<DomainEvent<?>> events = aggregate.pullDomainEvents();

        assertEquals(1, events.size());
        assertFalse(aggregate.hasDomainEvents());
        assertTrue(events.get(0) instanceof TestDomainEvent);
        assertEquals("new-name", ((TestDomainEvent) events.get(0)).getName());
    }

    @Test
    void shouldRejectNullDomainEvent() {
        TestAggregate aggregate = new TestAggregate("order-1");

        assertThrows(NullPointerException.class, aggregate::raiseNullEvent);
    }

    private static final class TestAggregate extends AggregateRoot<String> {

        private final String id;

        private TestAggregate(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        private void rename(String name) {
            registerEvent(new TestDomainEvent(name));
        }

        private void raiseNullEvent() {
            registerEvent(null);
        }
    }

    private static final class TestDomainEvent extends DomainEvent<OrderId> {

        private final String name;

        private TestDomainEvent(String name) {
            super(new EntityIdPath(new OrderId(name)));
            this.name = name;
        }

        public String getName() {
            return name;
        }

    }

    private static final class OrderId implements AggregateRootId {

        private static final EntityType TYPE = new TestEntityType("Order");

        private final String value;

        OrderId(String value) {
            this.value = value;
        }

        @Override
        public EntityType getType() {
            return TYPE;
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public String asTypedString() {
            return TYPE.asString() + " " + value;
        }

        @Override
        public String toString() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof OrderId)) {
                return false;
            }
            return value.equals(((OrderId) o).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    private static final class TestEntityType implements EntityType {

        private final String name;

        TestEntityType(String name) {
            this.name = name;
        }

        @Override
        public String asString() {
            return name;
        }
    }
}
