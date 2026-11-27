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

/** \u9a8c\u8bc1 <code>AggregateRoot</code> \u5728<code>shouldPublishBufferedLocalEvents</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class AggregateRootTest {
    /** \u9a8c\u8bc1<code>aggregate.pullDomainEvents().size()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>aggregate.pullDomainEvents().size()</code> \u7b49\u4e8e <code>0</code>\u3002 */
    @Test
    void shouldCollectNewEventsButNotReplayHistory() {
        AppliedAggregate aggregate = new AppliedAggregate();
        TestEvent created = new TestEvent();
        aggregate.record(created);
        assertEquals(1, aggregate.pullDomainEvents().size());
        aggregate.loadFromHistory(Collections.<DomainEvent<?>>singletonList(new TestEvent()));
        assertEquals(0, aggregate.pullDomainEvents().size());
    }
    /** \u9a8c\u8bc1<code>aggregate.handled</code> \u7b49\u4e8e <code>1</code>\uff0c<code>aggregate.handled</code> \u7b49\u4e8e <code>2</code>\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
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
    /** \u9a8c\u8bc1<code>second.id()</code> \u7b49\u4e8e <code>first.id()</code>\uff0c<code>first.sameIdentityAs(second)</code> \u7b49\u4e8e <code>true</code>\u3002 */
    @Test
    void shouldExposeDomainIdentityAndCompareEntitiesById() {
        IdentityAggregate first = new IdentityAggregate(new StringEntityId("same"));
        IdentityAggregate second = new IdentityAggregate(new StringEntityId("same"));
        assertEquals(first.id(), second.id());
        assertEquals(true, first.sameIdentityAs(second));
    }
    /** \u9a8c\u8bc1<code>aggregate.save()</code> \u7b49\u4e8e <code>aggregate</code>\uff0c<code>repository.saves</code> \u7b49\u4e8e <code>2</code>\uff0c<code>repository.deletes</code> \u7b49\u4e8e <code>1</code>\u3002 */
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
    /** \u4f9b\u6d4b\u8bd5\u9a8c\u8bc1\u4e8b\u4ef6\u5e94\u7528\u548c\u6301\u4e45\u5316\u7684\u805a\u5408\u6837\u672c\u3002 */
    private static final class AppliedAggregate extends AggregateRoot<StringEntityId> {
        /**
         * \u8c03\u7528 <code>apply()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
        void record(TestEvent event) { apply(event); }
        /**
         * \u4f5c\u4e3a\u7a7a\u5b9e\u73b0\u7684\u6d4b\u8bd5\u66ff\u8eab\uff0c\u8c03\u7528\u65f6\u4fdd\u6301\u5f53\u524d\u5939\u5177\u72b6\u6001\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
        @EventHandler private void on(TestEvent event) { }
        /**
         * \u521b\u5efa <code>new StringEntityId</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new StringEntityId("order-1")</code>
         */
        @Override public StringEntityId id() { return new StringEntityId("order-1"); }
    }
    /** \u6d4b\u8bd5\u4e13\u7528\u9886\u57df\u4e8b\u4ef6\uff0c\u7528\u4e8e\u63a7\u5236\u4e8b\u4ef6\u7c7b\u578b\u548c\u8d1f\u8f7d\u3002 */
    private static final class TestEvent extends DomainEvent<TestRootId> {
        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TestEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        TestEvent() { super(new EntityIdPath(new TestRootId("order-1"))); }
    }
    /** \u4e3a\u6d4b\u8bd5\u805a\u5408\u63d0\u4f9b\u72ec\u7acb\u6807\u8bc6\u7684\u503c\u5bf9\u8c61\u3002 */
    private static final class TestRootId implements AggregateRootId {
        /** \u6d4b\u8bd5\u805a\u5408\u6ce8\u518c\u548c\u53cd\u5e8f\u5217\u5316\u6240\u7528\u7684\u5b9e\u4f53\u7c7b\u578b\u6807\u8bc6\u3002 */
        private static final EntityType TYPE = new StringEntityType("Order");
        /** \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c\u3002 */
        private final String value;
        /**
         * \u8bbe\u7f6e <code>value</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>TestRootId</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param value \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c
         */
        private TestRootId(String value) { this.value = value; }
        /** {@inheritDoc} */
        @Override public EntityType getType() { return TYPE; }
        /** {@inheritDoc} */
        @Override public String asString() { return value; }
        /** {@inheritDoc} */
        @Override public String asTypedString() { return TYPE.asString() + ":" + value; }
    }
    /** \u4f9b\u6d4b\u8bd5\u9a8c\u8bc1\u4e8b\u4ef6\u5e94\u7528\u548c\u6301\u4e45\u5316\u7684\u805a\u5408\u6837\u672c\u3002 */
    private static final class HandlingAggregate extends AggregateRoot<StringEntityId> {
        /** \u5f53\u524d\u6d4b\u8bd5\u4e2d\u5df2\u7ecf\u4ea4\u7ed9\u5904\u7406\u5668\u7684\u8bb0\u5f55\u3002 */
        private int handled;
        /**
         * \u8c03\u7528 <code>apply()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
        void record(TestEvent event) { apply(event); }
        /**
         * \u8bb0\u5f55\u805a\u5408\u5df2\u5904\u7406\u4e00\u6761\u6d4b\u8bd5\u4e8b\u4ef6\u3002
         * @param event \u7531\u805a\u5408\u6216\u76d1\u542c\u5668\u6536\u5230\u7684\u6d4b\u8bd5\u4e8b\u4ef6
         */
        @EventHandler private void on(TestEvent event) { handled++; }
        /**
         * \u521b\u5efa <code>new StringEntityId</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new StringEntityId("order-1")</code>
         */
        @Override public StringEntityId id() { return new StringEntityId("order-1"); }
    }
    /** \u4f9b\u6d4b\u8bd5\u9a8c\u8bc1\u4e8b\u4ef6\u5e94\u7528\u548c\u6301\u4e45\u5316\u7684\u805a\u5408\u6837\u672c\u3002 */
    private static final class IdentityAggregate extends AggregateRoot<StringEntityId> {
        /** \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6\u3002 */
        private final StringEntityId id;
        /**
         * \u8bbe\u7f6e <code>id</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>IdentityAggregate</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param id \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6
         */
        private IdentityAggregate(StringEntityId id) { this.id = id; }
        /**
         * \u8bfb\u53d6<code>id</code> \u5b57\u6bb5\u3002
         * @return <code>id</code> \u5b57\u6bb5
         */
        @Override public StringEntityId id() { return id; }
    }
    /** \u4f9b\u6d4b\u8bd5\u9a8c\u8bc1\u4e8b\u4ef6\u5e94\u7528\u548c\u6301\u4e45\u5316\u7684\u805a\u5408\u6837\u672c\u3002 */
    private static final class PersistentAggregate extends AggregateRoot<StringEntityId> {
        /** \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6\u3002 */
        private final StringEntityId id;
            /**
             * \u8bbe\u7f6e <code>id</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>PersistentAggregate</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
             * @param id \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6
             */
            private PersistentAggregate(StringEntityId id) { this.id = id; }
        /**
         * \u8bfb\u53d6<code>id</code> \u5b57\u6bb5\u3002
         * @return <code>id</code> \u5b57\u6bb5
         */
        @Override public StringEntityId id() { return id; }
    }
    /** \u8bb0\u5f55\u4ed3\u50a8\u64cd\u4f5c\uff0c\u4f9b\u6d4b\u8bd5\u6838\u5bf9\u547d\u4ee4\u6216\u6295\u5f71\u5199\u5165\u3002 */
    private static final class RecordingRepository implements Repository<PersistentAggregate, StringEntityId> {
        /** \u7528\u4e8e\u89c2\u5bdf\u4ed3\u50a8\u5199\u5165\u8c03\u7528\u6b21\u6570\u6216\u8bb0\u5f55\u7684\u8ba1\u6570\u3002 */
        private int saves;
            /** \u6d4b\u8bd5\u66ff\u8eab\u6536\u5230\u7684\u5220\u9664\u64cd\u4f5c\u6b21\u6570\u3002 */
            private int deletes;
        /**
         * \u901a\u8fc7 <code>Optional.empty()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>Optional.empty()</code>
         */
        @Override public Optional<PersistentAggregate> findById(StringEntityId id) { return Optional.empty(); }
        /** {@inheritDoc} */
        @Override public PersistentAggregate save(PersistentAggregate aggregate) { saves++; return aggregate; }
        /**
         * \u8bb0\u5f55\u5bf9\u6d4b\u8bd5\u5b9e\u4f53\u6267\u884c\u7684\u4e00\u6b21\u5220\u9664\u8c03\u7528\u3002
         * @param id \u88ab\u67e5\u627e\u3001\u5199\u5165\u6216\u5220\u9664\u7684\u5939\u5177\u8bb0\u5f55\u4e3b\u952e
         */
        @Override public void deleteById(StringEntityId id) { deletes++; }
    }
    /** \u9a8c\u8bc1<code>aggregate.hasDomainEvents()</code>\u4e3a\u771f\uff0c<code>aggregate.domainEvents().size()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>((TestDomainEvent) events.get(0)).getName()</code> \u7b49\u4e8e <code>"new-name"</code>\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void shouldRejectNullDomainEvent() {
        TestAggregate aggregate = new TestAggregate("order-1");

        assertThrows(NullPointerException.class, aggregate::raiseNullEvent);
    }

    /** \u6d4b\u8bd5\u4e13\u7528\u805a\u5408\uff0c\u4f9b\u9a8c\u8bc1\u805a\u5408\u6807\u8bc6\u548c\u4ed3\u50a8\u6216\u4e8b\u4ef6\u5904\u7406\u884c\u4e3a\u3002 */
    private static final class TestAggregate extends AggregateRoot<String> {

        /** \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6\u3002 */
        private final String id;

        /**
         * \u8bbe\u7f6e <code>id</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>TestAggregate</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param id \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6
         */
        private TestAggregate(String id) {
            this.id = id;
        }

        /**
         * \u8bfb\u53d6<code>id</code> \u5b57\u6bb5\u3002
         * @return <code>id</code> \u5b57\u6bb5
         */
        @Override
        public String id() {
            return id;
        }

        /**
         * \u8c03\u7528 <code>registerEvent()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
         */
        private void rename(String name) {
            registerEvent(new TestDomainEvent(name));
        }

        /** \u8c03\u7528 <code>registerEvent()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002 */
        private void raiseNullEvent() {
            registerEvent(null);
        }
    }

    /** \u4f9b\u4f20\u8f93\u3001\u5e8f\u5217\u5316\u6216\u5904\u7406\u5668\u5206\u53d1\u9a8c\u8bc1\u7684\u9886\u57df\u4e8b\u4ef6\u6837\u672c\u3002 */
    private static final class TestDomainEvent extends DomainEvent<OrderId> {

        /** \u5f53\u524d\u6d4b\u8bd5\u6a21\u578b\u7528\u4e8e\u5c55\u793a\u548c\u65ad\u8a00\u7684\u540d\u79f0\u3002 */
        private final String name;

        /**
         * \u8bbe\u7f6e <code>name</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>TestDomainEvent</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
         */
        private TestDomainEvent(String name) {
            super(new EntityIdPath(new OrderId(name)));
            this.name = name;
        }

        /**
         * \u8bfb\u53d6<code>name</code> \u5b57\u6bb5\u3002
         * @return <code>name</code> \u5b57\u6bb5
         */
        public String getName() {
            return name;
        }

    }

    /** \u8ba2\u5355\u6d4b\u8bd5\u805a\u5408\u7684\u6807\u8bc6\u7c7b\u578b\u3002 */
    private static final class OrderId implements AggregateRootId {

        /** \u6d4b\u8bd5\u805a\u5408\u6ce8\u518c\u548c\u53cd\u5e8f\u5217\u5316\u6240\u7528\u7684\u5b9e\u4f53\u7c7b\u578b\u6807\u8bc6\u3002 */
        private static final EntityType TYPE = new TestEntityType("Order");

        /** \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c\u3002 */
        private final String value;

        /**
         * \u8bbe\u7f6e <code>value</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>OrderId</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param value \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c
         */
        OrderId(String value) {
            this.value = value;
        }

        /** {@inheritDoc} */
        @Override
        public EntityType getType() {
            return TYPE;
        }

        /** {@inheritDoc} */
        @Override
        public String asString() {
            return value;
        }

        /** {@inheritDoc} */
        @Override
        public String asTypedString() {
            return TYPE.asString() + " " + value;
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return value;
        }

        /** {@inheritDoc} */
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

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    /** \u5b9e\u73b0 <code>EntityType</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>asString()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static final class TestEntityType implements EntityType {

        /** \u5f53\u524d\u6d4b\u8bd5\u6a21\u578b\u7528\u4e8e\u5c55\u793a\u548c\u65ad\u8a00\u7684\u540d\u79f0\u3002 */
        private final String name;

        /**
         * \u8bbe\u7f6e <code>name</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>TestEntityType</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
         */
        TestEntityType(String name) {
            this.name = name;
        }

        /** {@inheritDoc} */
        @Override
        public String asString() {
            return name;
        }
    }
}
