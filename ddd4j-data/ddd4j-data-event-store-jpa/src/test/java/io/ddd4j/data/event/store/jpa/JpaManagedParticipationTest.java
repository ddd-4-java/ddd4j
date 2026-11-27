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
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;

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

    /**
     * 测试事件流中使用的聚合类型名称。
     */
    private static final String ORDER_TYPE = "Order";

    /**
     * 创建 <code>new EventPayloadSerializer</code> 供当前测试路径调用。
     *
     * @return 当前测试使用的结果，取自 <code>new EventPayloadSerializer(JsonMapper.builder().findAndAddModules().build())</code>
     */
    private static EventPayloadSerializer serializer() {
        return new EventPayloadSerializer(JsonMapper.builder().findAndAddModules().build());
    }

    /**
     * 按 <code>("isJoinedToTransaction".equals(name))</code> 等实际条件选择当前用例返回的结果。
     *
     * @param joined     传给 <code>Proxy.newProxyInstance()</code> 的 <code>boolean</code> 输入
     * @param accesses   传给 <code>Proxy.newProxyInstance()</code> 的 <code>AtomicInteger</code> 输入
     * @param flushCalls 传给 <code>Proxy.newProxyInstance()</code> 的 <code>AtomicInteger</code> 输入
     * @return 根据测试条件返回的可能值，包括 <code>joined</code>、<code>emptyQuery()</code>
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
     * 按 <code>("setMaxResults".equals(name) || "setLockMode".equals(name))</code> 等实际条件选择当前用例返回的结果。
     *
     * @return 根据测试条件返回的可能值，包括 <code>proxy</code>、<code>Collections.emptyList()</code>
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

    /**
     * 验证<code>entityManagerAccesses.get()</code> 等于 <code>0</code>。
     */
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

    /**
     * 验证<code>JpaEventStore.participatingManaged(null, repository, serializer, marker)</code> 抛出 <code>NullPointerException.class</code>，<code>JpaEventStore.participatingManaged(entityManager, (Runnable) null)</code> 抛出 <code>NullPointerException.class</code>，<code>entityManagerAccesses.get()</code> 等于 <code>0</code>。
     */
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

    /**
     * 验证<code>eventStore.read(ORDER_TYPE, new TestAggregateRootId("not-joined"))</code> 抛出 <code>IllegalStateException.class</code>，<code>markerCalls.get()</code> 等于 <code>0</code>。
     */
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

    /**
     * 验证<code>eventStore.append(ORDER_TYPE, new TestAggregateRootId("empty"), Collections.emptyList(), 0)</code> 抛出 <code>IllegalStateException.class</code>，<code>markerCalls.get()</code> 等于 <code>0</code>。
     */
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

    /**
     * 验证<code>repository.savedEvents</code> 等于 <code>1</code>，<code>flushCalls.get()</code> 等于 <code>1</code>。
     */
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

    /**
     * 验证<code>eventStore.read(ORDER_TYPE, new TestAggregateRootId("repository-failure"))</code> 抛出 <code>RuntimeException.class</code>，<code>failure</code> 与…为同一实例 <code>original</code>，<code>failure.getSuppressed()[0]</code> 与…为同一实例 <code>markerFailure</code>。
     */
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

    /**
     * 验证<code>eventStore.read(ORDER_TYPE, new TestAggregateRootId("serializer-failure"))</code> 抛出 <code>RuntimeException.class</code>，<code>failure</code> 与…为同一实例 <code>original</code>，<code>markerCalls.get()</code> 等于 <code>1</code>。
     */
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

    /**
     * 记录仓储操作，供测试核对命令或投影写入。
     */
    private static final class RecordingRepository implements JpaStoredEventRepository {

        /**
         * 测试事件读操作要报告或记录的失败。
         */
        private RuntimeException readFailure;
        /**
         * 保存用例从事件或仓储读回的结果。
         */
        private List<StoredEventEntity> readResults = Collections.emptyList();
        /**
         * 内存存储实际收到的领域事件。
         */
        private int savedEvents;

        /**
         * 查询指定聚合事件流的当前版本。
         *
         * @param aggregateType 聚合类型
         * @param aggregateId   聚合根标识
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
         * @param aggregateId   聚合根标识
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
         * @param aggregateId   聚合根标识
         * @param fromVersion   起始版本（包含）
         * @param toVersion     结束版本（包含）
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
         * @param limit        最大返回条数
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

    /**
     * 测试专用聚合根标识，供仓储、事件序列化或命令路由构造独立的聚合实例。
     */
    private static final class TestAggregateRootId implements AggregateRootId {

        /**
         * 测试聚合注册和反序列化所用的实体类型标识。
         */
        private static final EntityType TYPE = new StringEntityType("Order");
        /**
         * 用于组成测试标识的原始值。
         */
        private final String value;

        /**
         * 以给定状态构造 <code>TestAggregateRootId</code> 测试对象。
         *
         * @param value 用于组成测试标识的原始值
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

    /**
     * 测试专用领域事件，用于控制事件类型和负载。
     */
    private static final class TestEvent extends DomainEvent<TestAggregateRootId> {

        /**
         * 使用 <code>super()</code> 初始化 <code>TestEvent</code> 测试夹具。
         */
        private TestEvent() {
            super(new EntityIdPath(new TestAggregateRootId("event")));
        }
    }
}
