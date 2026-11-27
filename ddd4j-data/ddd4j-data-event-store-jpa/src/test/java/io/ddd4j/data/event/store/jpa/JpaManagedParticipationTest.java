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
import io.ddd4j.core.ddd.event.*;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 容器管理事务参与入口的受控契约测试；这些测试替身不构成真实 JTA 证明。
 */
class JpaManagedParticipationTest {

    /** \u6d4b\u8bd5\u4e8b\u4ef6\u6d41\u4e2d\u4f7f\u7528\u7684\u805a\u5408\u7c7b\u578b\u540d\u79f0\u3002 */
    private static final String ORDER_TYPE = "Order";

    /**
     * \u521b\u5efa <code>new EventPayloadSerializer</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new EventPayloadSerializer(JsonMapper.builder().findAndAddModules().build())</code>
     */
    private static EventPayloadSerializer serializer() {
        return new EventPayloadSerializer(JsonMapper.builder().findAndAddModules().build());
    }

    /**
     * \u6309 <code>("isJoinedToTransaction".equals(name))</code> \u7b49\u5b9e\u9645\u6761\u4ef6\u9009\u62e9\u5f53\u524d\u7528\u4f8b\u8fd4\u56de\u7684\u7ed3\u679c\u3002
     * @param joined \u4f20\u7ed9 <code>Proxy.newProxyInstance()</code> \u7684 <code>boolean</code> \u8f93\u5165
     * @param accesses \u4f20\u7ed9 <code>Proxy.newProxyInstance()</code> \u7684 <code>AtomicInteger</code> \u8f93\u5165
     * @param flushCalls \u4f20\u7ed9 <code>Proxy.newProxyInstance()</code> \u7684 <code>AtomicInteger</code> \u8f93\u5165
     * @return \u6839\u636e\u6d4b\u8bd5\u6761\u4ef6\u8fd4\u56de\u7684\u53ef\u80fd\u503c\uff0c\u5305\u62ec <code>joined</code>\u3001<code>emptyQuery()</code>
     */
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

    /**
     * \u6309 <code>("setMaxResults".equals(name) || "setLockMode".equals(name))</code> \u7b49\u5b9e\u9645\u6761\u4ef6\u9009\u62e9\u5f53\u524d\u7528\u4f8b\u8fd4\u56de\u7684\u7ed3\u679c\u3002
     * @return \u6839\u636e\u6d4b\u8bd5\u6761\u4ef6\u8fd4\u56de\u7684\u53ef\u80fd\u503c\uff0c\u5305\u62ec <code>proxy</code>\u3001<code>Collections.emptyList()</code>
     */
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

    /** \u9a8c\u8bc1<code>entityManagerAccesses.get()</code> \u7b49\u4e8e <code>0</code>\u3002 */
    @Test
    void managedFactoryConstructionMustNotRequireAnActiveTransaction() {
        AtomicInteger entityManagerAccesses = new AtomicInteger();
        EntityManager entityManager = entityManager(false, entityManagerAccesses, new AtomicInteger());

        assertDoesNotThrow(() -> JpaEventStore.participatingManaged(entityManager, () -> {
        }));
        assertDoesNotThrow(() -> JpaEventStore.participatingManaged(entityManager,
                new RecordingRepository(), serializer(), () -> {
                }));
        assertEquals(0, entityManagerAccesses.get());
    }

    /** \u9a8c\u8bc1<code>JpaEventStore.participatingManaged(null, repository, serializer, marker)</code> \u629b\u51fa <code>NullPointerException.class</code>\uff0c<code>JpaEventStore.participatingManaged(entityManager, (Runnable) null)</code> \u629b\u51fa <code>NullPointerException.class</code>\uff0c<code>entityManagerAccesses.get()</code> \u7b49\u4e8e <code>0</code>\u3002 */
    @Test
    void managedFactoriesMustRejectNullArgumentsBeforeAccessingEntityManager() {
        AtomicInteger entityManagerAccesses = new AtomicInteger();
        EntityManager entityManager = entityManager(false, entityManagerAccesses, new AtomicInteger());
        RecordingRepository repository = new RecordingRepository();
        EventPayloadSerializer serializer = serializer();
        Runnable marker = () -> {
        };

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

    /** \u9a8c\u8bc1<code>eventStore.read(ORDER_TYPE, new TestAggregateRootId("not-joined"))</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>markerCalls.get()</code> \u7b49\u4e8e <code>0</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>eventStore.append(ORDER_TYPE, new TestAggregateRootId("empty"), Collections.emptyList(), 0)</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>markerCalls.get()</code> \u7b49\u4e8e <code>0</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>repository.savedEvents</code> \u7b49\u4e8e <code>1</code>\uff0c<code>flushCalls.get()</code> \u7b49\u4e8e <code>1</code>\u3002 */
    @Test
    void managedOperationsMustNotUseResourceLocalOrEntityManagerLifecycleMethods() {
        AtomicInteger flushCalls = new AtomicInteger();
        RecordingRepository repository = new RecordingRepository();
        JpaEventStore eventStore = JpaEventStore.participatingManaged(
                entityManager(true, new AtomicInteger(), flushCalls), repository, serializer(), () -> {
                });

        assertDoesNotThrow(() -> eventStore.read(ORDER_TYPE, new TestAggregateRootId("read")));
        assertDoesNotThrow(() -> eventStore.append(ORDER_TYPE, new TestAggregateRootId("append"),
                Collections.<DomainEvent<?>>singletonList(new TestEvent()), 0));
        assertEquals(1, repository.savedEvents);
        assertEquals(1, flushCalls.get());
    }

    /** \u9a8c\u8bc1<code>eventStore.read(ORDER_TYPE, new TestAggregateRootId("repository-failure"))</code> \u629b\u51fa <code>RuntimeException.class</code>\uff0c<code>failure</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>original</code>\uff0c<code>failure.getSuppressed()[0]</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>markerFailure</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>eventStore.read(ORDER_TYPE, new TestAggregateRootId("serializer-failure"))</code> \u629b\u51fa <code>RuntimeException.class</code>\uff0c<code>failure</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>original</code>\uff0c<code>markerCalls.get()</code> \u7b49\u4e8e <code>1</code>\u3002 */
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

    /** \u8bb0\u5f55\u4ed3\u50a8\u64cd\u4f5c\uff0c\u4f9b\u6d4b\u8bd5\u6838\u5bf9\u547d\u4ee4\u6216\u6295\u5f71\u5199\u5165\u3002 */
    private static final class RecordingRepository implements JpaStoredEventRepository {

        /** \u6d4b\u8bd5\u4e8b\u4ef6\u8bfb\u64cd\u4f5c\u8981\u62a5\u544a\u6216\u8bb0\u5f55\u7684\u5931\u8d25\u3002 */
        private RuntimeException readFailure;
        /** \u4fdd\u5b58\u7528\u4f8b\u4ece\u4e8b\u4ef6\u6216\u4ed3\u50a8\u8bfb\u56de\u7684\u7ed3\u679c\u3002 */
        private List<StoredEventEntity> readResults = Collections.emptyList();
        /** \u5185\u5b58\u5b58\u50a8\u5b9e\u9645\u6536\u5230\u7684\u9886\u57df\u4e8b\u4ef6\u3002 */
        private int savedEvents;

        /**
         * 查询指定聚合事件流的当前版本。
         *
         * @param aggregateType 聚合类型
         * @param aggregateId 聚合根标识
         * @return 实际返回<code>0</code>，供调用方取得当前值
         */
        @Override
        public long findCurrentVersion(String aggregateType, String aggregateId) {
            return 0;
        }

        /**
         * 按版本升序查询指定聚合的事件记录。
         *
         * @param aggregateType 聚合类型
         * @param aggregateId 聚合根标识
         * @return 实际返回<code>readResults</code>，供调用方取得当前值
         */
        @Override
        public List<StoredEventEntity> findByAggregateTypeAndAggregateIdOrderByVersionAsc(
                String aggregateType, String aggregateId) {
            if (Objects.nonNull(readFailure)) {
                throw readFailure;
            }
            return readResults;
        }

        /**
         * 按版本升序查询指定聚合在给定版本范围内的事件记录。
         *
         * @param aggregateType 聚合类型
         * @param aggregateId 聚合根标识
         * @param fromVersion 起始版本（包含）
         * @param toVersion 结束版本（包含）
         * @return 实际返回<code>readResults</code>，供调用方取得当前值
         */
        @Override
        public List<StoredEventEntity> findByAggregateTypeAndAggregateIdAndVersionBetweenOrderByVersionAsc(
                String aggregateType, String aggregateId, long fromVersion, long toVersion) {
            return readResults;
        }

        /**
         * 按全局位置升序读取起点及其后的事件记录。
         *
         * @param fromPosition 起始 position（包含）
         * @param limit 最大返回条数
         * @return 实际返回<code>readResults</code>，供调用方取得当前值
         */
        @Override
        public List<StoredEventEntity> findByPositionGreaterThanEqualOrderByPositionAsc(
                long fromPosition, int limit) {
            return readResults;
        }

        /**
         * 写入或更新给定的领域对象或存储记录。
         *
         * @param entity 事件实体
         */
        @Override
        public void save(StoredEventEntity entity) {
            savedEvents++;
        }

        /**
         * 分配事件存储中的下一个全局位置。
         *
         * @return 实际返回<code>savedEvents + 1L</code>，供调用方取得当前值
         */
        @Override
        public long nextPosition() {
            return savedEvents + 1L;
        }
    }

    /** \u6d4b\u8bd5\u4e13\u7528\u805a\u5408\u6839\u6807\u8bc6\uff0c\u4f9b\u4ed3\u50a8\u3001\u4e8b\u4ef6\u5e8f\u5217\u5316\u6216\u547d\u4ee4\u8def\u7531\u6784\u9020\u72ec\u7acb\u7684\u805a\u5408\u5b9e\u4f8b\u3002 */
    private static final class TestAggregateRootId implements AggregateRootId {

        /** \u6d4b\u8bd5\u805a\u5408\u6ce8\u518c\u548c\u53cd\u5e8f\u5217\u5316\u6240\u7528\u7684\u5b9e\u4f53\u7c7b\u578b\u6807\u8bc6\u3002 */
        private static final EntityType TYPE = new StringEntityType("Order");
        /** \u7528\u4e8e\u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c\u3002 */
        private final String value;

        /**
         * \u4ee5\u7ed9\u5b9a\u72b6\u6001\u6784\u9020 <code>TestAggregateRootId</code> \u6d4b\u8bd5\u5bf9\u8c61\u3002
         * @param value \u7528\u4e8e\u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c
         */
        private TestAggregateRootId(String value) {
            this.value = value;
        }

        /**
         * 返回当前值或标识公开的类型。
         *
         * @return 实际返回<code>TYPE</code>，供调用方取得当前值
         */
        @Override
        public EntityType getType() {
            return TYPE;
        }

        /**
         * 返回聚合或实体标识的业务字符串。
         *
         * @return 实际返回<code>value</code>，供调用方取得当前值
         */
        @Override
        public String asString() {
            return value;
        }

        /**
         * 返回带类型前缀的标识字符串。
         *
         * <p>测试夹具会调用 <code>TYPE.asString()</code>。</p>
         *
         * @return 实际返回<code>TYPE.asString() + ":" + value</code>
         */
        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }
    }

    /** \u6d4b\u8bd5\u4e13\u7528\u9886\u57df\u4e8b\u4ef6\uff0c\u7528\u4e8e\u63a7\u5236\u4e8b\u4ef6\u7c7b\u578b\u548c\u8d1f\u8f7d\u3002 */
    private static final class TestEvent extends DomainEvent<TestAggregateRootId> {

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TestEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        private TestEvent() {
            super(new EntityIdPath(new TestAggregateRootId("event")));
        }
    }
}
