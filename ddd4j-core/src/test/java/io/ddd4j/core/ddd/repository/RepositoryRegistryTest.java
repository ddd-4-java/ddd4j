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
package io.ddd4j.core.ddd.repository;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.event.StringEntityId;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.exception.BizRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 仓储注册表的聚合、查询、上下文优先级及清理契约测试。
 */
class RepositoryRegistryTest {

    /**
     * 用于比较线程作用域与全局服务解析优先级的仓储。
     */
    private StubOrderRepository globalRepo;
    /**
     * 用来验证请求级覆盖优先于全局注册的仓储。
     */
    private StubOrderRepository threadRepo;

    /**
     * 测试执行前执行 <code>BaseContext.clear()</code>、<code>ThreadContext.clear()</code>、<code>RepositoryRegistry.unregister()</code>、<code>RepositoryRegistry.unregisterQuery()</code> 完成资源准备。
     */
    @BeforeEach
    void setUp() {
        BaseContext.clear();
        ThreadContext.clear();
        RepositoryRegistry.unregister(Order.class);
        RepositoryRegistry.unregister(TestAggregate.class);
        RepositoryRegistry.unregisterQuery(OrderQuery.class);
        globalRepo = new StubOrderRepository("global");
        threadRepo = new StubOrderRepository("thread");
    }

    /**
     * 测试结束后执行 <code>BaseContext.clear()</code>、<code>ThreadContext.clear()</code>、<code>RepositoryRegistry.unregister()</code>、<code>RepositoryRegistry.unregisterQuery()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void tearDown() {
        BaseContext.clear();
        ThreadContext.clear();
        RepositoryRegistry.unregister(Order.class);
        RepositoryRegistry.unregister(TestAggregate.class);
        RepositoryRegistry.unregisterQuery(OrderQuery.class);
    }

    /**
     * 验证<code>RepositoryRegistry.key(Order.class)</code> 等于 <code>"ddd4j.repository." + Order.class.getName()</code>。
     */
    @Test
    void key_shouldUsePrefixWithClassName() {
        assertThat(RepositoryRegistry.key(Order.class))
                .isEqualTo("ddd4j.repository." + Order.class.getName());
    }

    /**
     * 验证<code>resolved</code> 指向同一实例 <code>globalRepo</code>。
     */
    @Test
    void register_and_repository_shouldReturnSameInstance() {
        RepositoryRegistry.register(Order.class, globalRepo);

        Repository<Order, ?> resolved = RepositoryRegistry.repository(Order.class);

        assertThat(resolved).isSameAs(globalRepo);
    }

    /**
     * 验证<code>RepositoryRegistry.repository(Order.class)</code> 抛出 <code>BizRuntimeException.class</code>。
     */
    @Test
    void repository_shouldThrowBizRuntimeExceptionWhenNotRegistered() {
        assertThatThrownBy(() -> RepositoryRegistry.repository(Order.class))
                .isInstanceOf(BizRuntimeException.class)
                .hasMessageContaining("Repository not found for aggregate Order")
                .hasMessageContaining("Register it via RepositoryRegistry.register(Order, repository)");
    }

    /**
     * 验证<code>RepositoryRegistry.repositoryForQuery(OrderQuery.class)</code> 抛出 <code>BizRuntimeException.class</code>。
     */
    @Test
    void repositoryForQuery_shouldThrowWhenNotRegistered() {
        assertThatThrownBy(() -> RepositoryRegistry.repositoryForQuery(OrderQuery.class))
                .isInstanceOf(BizRuntimeException.class)
                .hasMessageContaining("Repository not found for query OrderQuery")
                .hasMessageContaining("Register it via RepositoryRegistry.register(modelClass, OrderQuery, repository)");
    }

    /**
     * 验证<code>RepositoryRegistry.repository(Order.class)</code> 指向同一实例 <code>globalRepo</code>，<code>RepositoryRegistry.repositoryForQuery(OrderQuery.class)</code> 指向同一实例 <code>globalRepo</code>。
     */
    @Test
    void registerWithQuery_shouldResolveBothAggregateAndQuery() {
        RepositoryRegistry.register(Order.class, OrderQuery.class, globalRepo);

        assertThat(RepositoryRegistry.repository(Order.class)).isSameAs(globalRepo);
        assertThat(RepositoryRegistry.repositoryForQuery(OrderQuery.class)).isSameAs(globalRepo);
    }

    /**
     * 验证<code>resolved</code> 指向同一实例 <code>threadRepo</code>。
     */
    @Test
    void threadContext_shouldOverrideBaseContext() {
        RepositoryRegistry.register(Order.class, globalRepo);
        ThreadContext.inject(RepositoryRegistry.key(Order.class), Repository.class, threadRepo);

        Repository<Order, ?> resolved = RepositoryRegistry.repository(Order.class);

        assertThat(resolved).isSameAs(threadRepo);
    }

    /**
     * 验证<code>RepositoryRegistry.repository(Order.class)</code> 指向同一实例 <code>globalRepo</code>，<code>RepositoryRegistry.repository(Order.class)</code> 抛出 <code>BizRuntimeException.class</code>。
     */
    @Test
    void unregister_shouldRemoveFromContextAndStaticMap() {
        RepositoryRegistry.register(Order.class, globalRepo);
        assertThat(RepositoryRegistry.repository(Order.class)).isSameAs(globalRepo);

        RepositoryRegistry.unregister(Order.class);

        assertThatThrownBy(() -> RepositoryRegistry.repository(Order.class))
                .isInstanceOf(BizRuntimeException.class);
    }

    /**
     * 验证<code>RepositoryRegistry.repositoryForQuery(OrderQuery.class)</code> 指向同一实例 <code>globalRepo</code>，<code>RepositoryRegistry.repositoryForQuery(OrderQuery.class)</code> 抛出 <code>BizRuntimeException.class</code>。
     */
    @Test
    void unregisterQuery_shouldRemoveQueryMapping() {
        RepositoryRegistry.register(Order.class, OrderQuery.class, globalRepo);
        assertThat(RepositoryRegistry.repositoryForQuery(OrderQuery.class)).isSameAs(globalRepo);

        RepositoryRegistry.unregisterQuery(OrderQuery.class);

        assertThatThrownBy(() -> RepositoryRegistry.repositoryForQuery(OrderQuery.class))
                .isInstanceOf(BizRuntimeException.class);
    }

    /**
     * 验证<code>RepositoryRegistry.repository(Order.class)</code> 指向同一实例 <code>globalRepo</code>，<code>RepositoryRegistry.repositoryForQuery(OrderQuery.class)</code> 指向同一实例 <code>globalRepo</code>，<code>RepositoryRegistry.repositoryForQuery(OrderQuery.class)</code> 抛出 <code>BizRuntimeException.class</code>。
     */
    @Test
    void clear_shouldRemoveAllRegistrations() {
        RepositoryRegistry.register(Order.class, OrderQuery.class, globalRepo);
        assertThat(RepositoryRegistry.repository(Order.class)).isSameAs(globalRepo);
        assertThat(RepositoryRegistry.repositoryForQuery(OrderQuery.class)).isSameAs(globalRepo);

        RepositoryRegistry.clear();

        assertThatThrownBy(() -> RepositoryRegistry.repository(Order.class))
                .isInstanceOf(BizRuntimeException.class);
        assertThatThrownBy(() -> RepositoryRegistry.repositoryForQuery(OrderQuery.class))
                .isInstanceOf(BizRuntimeException.class);
    }

    /**
     * 验证<code>RepositoryRegistry.repository(Order.class)</code> 指向同一实例 <code>threadRepo</code>，<code>RepositoryRegistry.repository(Order.class)</code> 指向同一实例 <code>globalRepo</code>。
     */
    @Test
    void repository_shouldFallbackToBaseContextAfterThreadRemoved() {
        RepositoryRegistry.register(Order.class, globalRepo);
        ThreadContext.inject(RepositoryRegistry.key(Order.class), Repository.class, threadRepo);
        assertThat(RepositoryRegistry.repository(Order.class)).isSameAs(threadRepo);

        ThreadContext.clear();

        assertThat(RepositoryRegistry.repository(Order.class)).isSameAs(globalRepo);
    }

    // ========================= Fixtures =========================

    /**
     * 验证<code>RepositoryRegistry.repository(TestAggregate.class)</code> 与…为同一实例 <code>repository</code>，<code>RepositoryRegistry.repository(TestAggregate.class).findById(aggregate.id()).get()</code> 等于 <code>aggregate</code>，<code>RepositoryRegistry.repository(TestAggregate.class)</code> 抛出 <code>BizRuntimeException.class</code>。
     */
    @Test
    void shouldRegisterResolveAndUnregisterRepository() {
        InMemoryRepository repository = new InMemoryRepository();
        RepositoryRegistry.register(TestAggregate.class, repository);
        assertSame(repository, RepositoryRegistry.repository(TestAggregate.class));
        TestAggregate aggregate = new TestAggregate(new StringEntityId("a-1"));
        repository.save(aggregate);
        assertEquals(aggregate, RepositoryRegistry.repository(TestAggregate.class).findById(aggregate.id()).get());
        RepositoryRegistry.unregister(TestAggregate.class);
        assertThrows(BizRuntimeException.class, () -> RepositoryRegistry.repository(TestAggregate.class));
    }

    /**
     * 供测试验证事件应用和持久化的聚合样本。
     */
    static final class Order extends AggregateRoot<String> {
        /**
         * 标识当前测试对象的主键或事件标识。
         */
        private final String id;

        /**
         * 设置 <code>id</code>，用于构造 <code>Order</code> 测试实例。
         *
         * @param id 标识当前测试对象的主键或事件标识
         */
        Order(String id) {
            this.id = id;
        }

        /**
         * 读取<code>id</code> 字段。
         *
         * @return <code>id</code> 字段
         */
        @Override
        public String id() {
            return id;
        }
    }

    /**
     * 订单测试查询对象，供构造仓储检索条件。
     */
    static final class OrderQuery extends Query<Order> {
    }

    /**
     * 提供可控制的数据读写以验证仓储调用契约。
     */
    static class StubOrderRepository implements Repository<Order, Serializable> {
        /**
         * 当前测试模型用于展示和断言的名称。
         */
        final String name;

        /**
         * 设置 <code>name</code>，用于构造 <code>StubOrderRepository</code> 测试实例。
         *
         * @param name 当前测试指定的对象或属性名称
         */
        StubOrderRepository(String name) {
            this.name = name;
        }

        /**
         * 通过 <code>Optional.of()</code> 取得当前测试需要的结果。
         *
         * @param id 当前要查找的测试记录标识
         * @return 当前测试使用的结果，取自 <code>Optional.of(new Order(String.valueOf(id)))</code>
         */
        @Override
        public Optional<Order> findById(Serializable id) {
            return Optional.of(new Order(String.valueOf(id)));
        }

        /**
         * 写入或更新给定的领域对象或存储记录。
         *
         * @param aggregate 待处理聚合根
         * @return 实际返回<code>aggregate</code>，供调用方取得当前值
         */
        @Override
        public Order save(Order aggregate) {
            return aggregate;
        }

        /**
         * 提供测试替身回调，调用时保持夹具状态。
         *
         * @param aggregate 待处理聚合根
         */
        @Override
        public void delete(Order aggregate) {
            // no-op
        }
    }

    /**
     * 测试专用聚合，供验证聚合标识和仓储或事件处理行为。
     */
    private static final class TestAggregate extends AggregateRoot<StringEntityId> {
        /**
         * 标识当前测试对象的主键或事件标识。
         */
        private final StringEntityId id;

        /**
         * 设置 <code>id</code>，用于构造 <code>TestAggregate</code> 测试实例。
         *
         * @param id 标识当前测试对象的主键或事件标识
         */
        private TestAggregate(StringEntityId id) {
            this.id = id;
        }

        /**
         * 读取<code>id</code> 字段。
         *
         * @return <code>id</code> 字段
         */
        @Override
        public StringEntityId id() {
            return id;
        }
    }

    /**
     * 提供可控制的数据读写以验证仓储调用契约。
     */
    private static final class InMemoryRepository implements Repository<TestAggregate, StringEntityId> {
        /**
         * 当前测试记录的属性值或批量输入集合。
         */
        private final Map<StringEntityId, TestAggregate> values = new HashMap<StringEntityId, TestAggregate>();

        /**
         * 通过 <code>Optional.ofNullable()</code> 取得当前测试需要的结果。
         *
         * @param id 当前要查找的测试记录标识
         * @return 当前测试使用的结果，取自 <code>Optional.ofNullable(values.get(id))</code>
         */
        @Override
        public Optional<TestAggregate> findById(StringEntityId id) {
            return Optional.ofNullable(values.get(id));
        }

        /**
         * 写入或更新给定的领域对象或存储记录。
         *
         * <p>测试夹具会调用 <code>values.put(aggregate.id(), aggregate)</code>、<code>aggregate.id()</code>。</p>
         *
         * @param aggregate 待处理聚合根
         * @return 实际返回<code>aggregate</code>
         */
        @Override
        public TestAggregate save(TestAggregate aggregate) {
            values.put(aggregate.id(), aggregate);
            return aggregate;
        }
    }
}
