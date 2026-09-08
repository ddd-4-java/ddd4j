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
package io.ddd4j.data.event.store.jpa;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.ddd4j.core.cqrs.eventstore.jackson.EventPayloadSerializer;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 容器管理事务参与入口的受控契约测试；这些测试替身不构成真实 JTA 证明。 */
class JpaManagedParticipationTest {

    private static final String ORDER_TYPE = "Order";

    @Test
    void managedFactoryConstructionMustNotRequireAnActiveTransaction() {
        AtomicInteger entityManagerAccesses = new AtomicInteger();
        EntityManager entityManager = entityManager(false, entityManagerAccesses, new AtomicInteger());

        assertDoesNotThrow(() -> JpaEventStore.participatingManaged(entityManager, () -> { }));
        assertDoesNotThrow(() -> JpaEventStore.participatingManaged(entityManager,
                new RecordingRepository(), serializer(), () -> { }));
        assertEquals(0, entityManagerAccesses.get());
    }

    @Test
    void managedFactoriesMustRejectNullArgumentsBeforeAccessingEntityManager() {
        AtomicInteger entityManagerAccesses = new AtomicInteger();
        EntityManager entityManager = entityManager(false, entityManagerAccesses, new AtomicInteger());
        RecordingRepository repository = new RecordingRepository();
        EventPayloadSerializer serializer = serializer();
        Runnable marker = () -> { };

        assertThrows(NullPointerException.class,
                () -> JpaEventStore.participatingManaged(null, repository, serializer, marker));
        assertThrows(NullPointerException.class,
                () -> JpaEventStore.participatingManaged(entityManager, (Runnable) null));
        assertThrows(NullPointerException.class,
                () -> JpaEventStore.participatingManaged(entityManager, null, serializer, marker));
        assertThrows(NullPointerException.class,
                () -> JpaEventStore.participatingManaged(entityManager, repository, null, marker));
        assertThrows(NullPointerException.class,
                () -> JpaEventStore.participatingManaged(entityManager, repository, serializer, null));
        assertEquals(0, entityManagerAccesses.get());
    }

    @Test
    void managedReadMustRejectWhenEntityManagerIsNotJoinedWithoutMarkingRollback() {
        AtomicInteger markerCalls = new AtomicInteger();
        JpaEventStore eventStore = JpaEventStore.participatingManaged(
                entityManager(false, new AtomicInteger(), new AtomicInteger()),
                new RecordingRepository(), serializer(), markerCalls::incrementAndGet);

        assertThrows(IllegalStateException.class,
                () -> eventStore.read(ORDER_TYPE, new TestAggregateRootId("not-joined")));
        assertEquals(0, markerCalls.get());
    }

    @Test
    void managedEmptyAppendMustRejectWhenEntityManagerIsNotJoinedWithoutMarkingRollback() {
        AtomicInteger markerCalls = new AtomicInteger();
        JpaEventStore eventStore = JpaEventStore.participatingManaged(
                entityManager(false, new AtomicInteger(), new AtomicInteger()),
                new RecordingRepository(), serializer(), markerCalls::incrementAndGet);

        assertThrows(IllegalStateException.class, () -> eventStore.append(ORDER_TYPE,
                new TestAggregateRootId("empty"), Collections.emptyList(), 0));
        assertEquals(0, markerCalls.get());
    }

    @Test
    void managedOperationsMustNotUseResourceLocalOrEntityManagerLifecycleMethods() {
        AtomicInteger flushCalls = new AtomicInteger();
        RecordingRepository repository = new RecordingRepository();
        JpaEventStore eventStore = JpaEventStore.participatingManaged(
                entityManager(true, new AtomicInteger(), flushCalls), repository, serializer(), () -> { });

        assertDoesNotThrow(() -> eventStore.read(ORDER_TYPE, new TestAggregateRootId("read")));
        assertDoesNotThrow(() -> eventStore.append(ORDER_TYPE, new TestAggregateRootId("append"),
                Collections.<DomainEvent<?>>singletonList(new TestEvent()), 0));
        assertEquals(1, repository.savedEvents);
        assertEquals(1, flushCalls.get());
    }

    @Test
    void managedRepositoryFailureMustKeepOriginalAndSuppressMarkerFailure() {
        RuntimeException original = new IllegalStateException("repository failure");
        RuntimeException markerFailure = new IllegalStateException("marker failure");
        RecordingRepository repository = new RecordingRepository();
        repository.readFailure = original;
        JpaEventStore eventStore = JpaEventStore.participatingManaged(
                entityManager(true, new AtomicInteger(), new AtomicInteger()), repository, serializer(), () -> {
                    throw markerFailure;
                });

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> eventStore.read(ORDER_TYPE, new TestAggregateRootId("repository-failure")));
        assertSame(original, failure);
        assertEquals(1, failure.getSuppressed().length);
        assertSame(markerFailure, failure.getSuppressed()[0]);
    }

    @Test
    void managedSerializerFailureMustKeepOriginalAndMarkRollback() {
        RuntimeException original = new IllegalStateException("serializer failure");
        AtomicInteger markerCalls = new AtomicInteger();
        RecordingRepository repository = new RecordingRepository();
        StoredEventEntity entity = new StoredEventEntity();
        entity.setEventType(TestEvent.class.getName());
        entity.setPayload("{}");
        repository.readResults = Collections.singletonList(entity);
        EventPayloadSerializer failingSerializer = new EventPayloadSerializer(
                JsonMapper.builder().findAndAddModules().build()) {
            @Override
            public DomainEvent<?> deserialize(String json, Class<? extends DomainEvent<?>> eventType) {
                throw original;
            }
        };
        JpaEventStore eventStore = JpaEventStore.participatingManaged(
                entityManager(true, new AtomicInteger(), new AtomicInteger()), repository,
                failingSerializer, markerCalls::incrementAndGet);

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> eventStore.read(ORDER_TYPE, new TestAggregateRootId("serializer-failure")));
        assertSame(original, failure);
        assertEquals(1, markerCalls.get());
    }

    private static EventPayloadSerializer serializer() {
        return new EventPayloadSerializer(JsonMapper.builder().findAndAddModules().build());
    }

    private static EntityManager entityManager(boolean joined, AtomicInteger accesses, AtomicInteger flushCalls) {
        return (EntityManager) Proxy.newProxyInstance(EntityManager.class.getClassLoader(),
                new Class<?>[]{EntityManager.class}, (proxy, method, arguments) -> {
                    String name = method.getName();
                    if ("isJoinedToTransaction".equals(name)) {
                        accesses.incrementAndGet();
                        return joined;
                    }
                    if ("createQuery".equals(name)) {
                        accesses.incrementAndGet();
                        return emptyQuery();
                    }
                    if ("flush".equals(name)) {
                        accesses.incrementAndGet();
                        flushCalls.incrementAndGet();
                        return null;
                    }
                    if ("getTransaction".equals(name) || "clear".equals(name) || "close".equals(name)) {
                        throw new AssertionError("Managed participation must not call EntityManager." + name);
                    }
                    if ("toString".equals(name)) {
                        return "ControlledEntityManager";
                    }
                    if ("hashCode".equals(name)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(name)) {
                        return proxy == arguments[0];
                    }
                    throw new AssertionError("Unexpected EntityManager call: " + name);
                });
    }

    private static TypedQuery<?> emptyQuery() {
        return (TypedQuery<?>) Proxy.newProxyInstance(TypedQuery.class.getClassLoader(),
                new Class<?>[]{TypedQuery.class}, (proxy, method, arguments) -> {
                    String name = method.getName();
                    if ("setMaxResults".equals(name) || "setLockMode".equals(name)) {
                        return proxy;
                    }
                    if ("getResultList".equals(name)) {
                        return Collections.emptyList();
                    }
                    throw new AssertionError("Unexpected query call: " + name);
                });
    }

    private static final class RecordingRepository implements JpaStoredEventRepository {

        private RuntimeException readFailure;
        private List<StoredEventEntity> readResults = Collections.emptyList();
        private int savedEvents;

        @Override
        public long findCurrentVersion(String aggregateType, String aggregateId) {
            return 0;
        }

        @Override
        public List<StoredEventEntity> findByAggregateTypeAndAggregateIdOrderByVersionAsc(
                String aggregateType, String aggregateId) {
            if (Objects.nonNull(readFailure)) {
                throw readFailure;
            }
            return readResults;
        }

        @Override
        public List<StoredEventEntity> findByAggregateTypeAndAggregateIdAndVersionBetweenOrderByVersionAsc(
                String aggregateType, String aggregateId, long fromVersion, long toVersion) {
            return readResults;
        }

        @Override
        public List<StoredEventEntity> findByPositionGreaterThanEqualOrderByPositionAsc(
                long fromPosition, int limit) {
            return readResults;
        }

        @Override
        public void save(StoredEventEntity entity) {
            savedEvents++;
        }

        @Override
        public long nextPosition() {
            return savedEvents + 1L;
        }
    }

    private static final class TestAggregateRootId implements AggregateRootId {

        private static final EntityType TYPE = new StringEntityType("Order");
        private final String value;

        private TestAggregateRootId(String value) {
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
            return TYPE.asString() + ":" + value;
        }
    }

    private static final class TestEvent extends DomainEvent<TestAggregateRootId> {

        private TestEvent() {
            super(new EntityIdPath(new TestAggregateRootId("event")));
        }
    }
}
