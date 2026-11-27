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

    /** \u7528\u4e8e\u6bd4\u8f83\u7ebf\u7a0b\u4f5c\u7528\u57df\u4e0e\u5168\u5c40\u670d\u52a1\u89e3\u6790\u4f18\u5148\u7ea7\u7684\u4ed3\u50a8\u3002 */
    private StubOrderRepository globalRepo;
    /** \u7528\u6765\u9a8c\u8bc1\u8bf7\u6c42\u7ea7\u8986\u76d6\u4f18\u5148\u4e8e\u5168\u5c40\u6ce8\u518c\u7684\u4ed3\u50a8\u3002 */
    private StubOrderRepository threadRepo;

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>BaseContext.clear()</code>\u3001<code>ThreadContext.clear()</code>\u3001<code>RepositoryRegistry.unregister()</code>\u3001<code>RepositoryRegistry.unregisterQuery()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
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

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>BaseContext.clear()</code>\u3001<code>ThreadContext.clear()</code>\u3001<code>RepositoryRegistry.unregister()</code>\u3001<code>RepositoryRegistry.unregisterQuery()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void tearDown() {
        BaseContext.clear();
        ThreadContext.clear();
        RepositoryRegistry.unregister(Order.class);
        RepositoryRegistry.unregister(TestAggregate.class);
        RepositoryRegistry.unregisterQuery(OrderQuery.class);
    }

    /** \u9a8c\u8bc1<code>RepositoryRegistry.key(Order.class)</code> \u7b49\u4e8e <code>"ddd4j.repository." + Order.class.getName()</code>\u3002 */
    @Test
    void key_shouldUsePrefixWithClassName() {
        assertThat(RepositoryRegistry.key(Order.class))
                .isEqualTo("ddd4j.repository." + Order.class.getName());
    }

    /** \u9a8c\u8bc1<code>resolved</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>globalRepo</code>\u3002 */
    @Test
    void register_and_repository_shouldReturnSameInstance() {
        RepositoryRegistry.register(Order.class, globalRepo);

        Repository<Order, ?> resolved = RepositoryRegistry.repository(Order.class);

        assertThat(resolved).isSameAs(globalRepo);
    }

    /** \u9a8c\u8bc1<code>RepositoryRegistry.repository(Order.class)</code> \u629b\u51fa <code>BizRuntimeException.class</code>\u3002 */
    @Test
    void repository_shouldThrowBizRuntimeExceptionWhenNotRegistered() {
        assertThatThrownBy(() -> RepositoryRegistry.repository(Order.class))
                .isInstanceOf(BizRuntimeException.class)
                .hasMessageContaining("Repository not found for aggregate Order")
                .hasMessageContaining("Register it via RepositoryRegistry.register(Order, repository)");
    }

    /** \u9a8c\u8bc1<code>RepositoryRegistry.repositoryForQuery(OrderQuery.class)</code> \u629b\u51fa <code>BizRuntimeException.class</code>\u3002 */
    @Test
    void repositoryForQuery_shouldThrowWhenNotRegistered() {
        assertThatThrownBy(() -> RepositoryRegistry.repositoryForQuery(OrderQuery.class))
                .isInstanceOf(BizRuntimeException.class)
                .hasMessageContaining("Repository not found for query OrderQuery")
                .hasMessageContaining("Register it via RepositoryRegistry.register(modelClass, OrderQuery, repository)");
    }

    /** \u9a8c\u8bc1<code>RepositoryRegistry.repository(Order.class)</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>globalRepo</code>\uff0c<code>RepositoryRegistry.repositoryForQuery(OrderQuery.class)</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>globalRepo</code>\u3002 */
    @Test
    void registerWithQuery_shouldResolveBothAggregateAndQuery() {
        RepositoryRegistry.register(Order.class, OrderQuery.class, globalRepo);

        assertThat(RepositoryRegistry.repository(Order.class)).isSameAs(globalRepo);
        assertThat(RepositoryRegistry.repositoryForQuery(OrderQuery.class)).isSameAs(globalRepo);
    }

    /** \u9a8c\u8bc1<code>resolved</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>threadRepo</code>\u3002 */
    @Test
    void threadContext_shouldOverrideBaseContext() {
        RepositoryRegistry.register(Order.class, globalRepo);
        ThreadContext.inject(RepositoryRegistry.key(Order.class), Repository.class, threadRepo);

        Repository<Order, ?> resolved = RepositoryRegistry.repository(Order.class);

        assertThat(resolved).isSameAs(threadRepo);
    }

    /** \u9a8c\u8bc1<code>RepositoryRegistry.repository(Order.class)</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>globalRepo</code>\uff0c<code>RepositoryRegistry.repository(Order.class)</code> \u629b\u51fa <code>BizRuntimeException.class</code>\u3002 */
    @Test
    void unregister_shouldRemoveFromContextAndStaticMap() {
        RepositoryRegistry.register(Order.class, globalRepo);
        assertThat(RepositoryRegistry.repository(Order.class)).isSameAs(globalRepo);

        RepositoryRegistry.unregister(Order.class);

        assertThatThrownBy(() -> RepositoryRegistry.repository(Order.class))
                .isInstanceOf(BizRuntimeException.class);
    }

    /** \u9a8c\u8bc1<code>RepositoryRegistry.repositoryForQuery(OrderQuery.class)</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>globalRepo</code>\uff0c<code>RepositoryRegistry.repositoryForQuery(OrderQuery.class)</code> \u629b\u51fa <code>BizRuntimeException.class</code>\u3002 */
    @Test
    void unregisterQuery_shouldRemoveQueryMapping() {
        RepositoryRegistry.register(Order.class, OrderQuery.class, globalRepo);
        assertThat(RepositoryRegistry.repositoryForQuery(OrderQuery.class)).isSameAs(globalRepo);

        RepositoryRegistry.unregisterQuery(OrderQuery.class);

        assertThatThrownBy(() -> RepositoryRegistry.repositoryForQuery(OrderQuery.class))
                .isInstanceOf(BizRuntimeException.class);
    }

    /** \u9a8c\u8bc1<code>RepositoryRegistry.repository(Order.class)</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>globalRepo</code>\uff0c<code>RepositoryRegistry.repositoryForQuery(OrderQuery.class)</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>globalRepo</code>\uff0c<code>RepositoryRegistry.repositoryForQuery(OrderQuery.class)</code> \u629b\u51fa <code>BizRuntimeException.class</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>RepositoryRegistry.repository(Order.class)</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>threadRepo</code>\uff0c<code>RepositoryRegistry.repository(Order.class)</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>globalRepo</code>\u3002 */
    @Test
    void repository_shouldFallbackToBaseContextAfterThreadRemoved() {
        RepositoryRegistry.register(Order.class, globalRepo);
        ThreadContext.inject(RepositoryRegistry.key(Order.class), Repository.class, threadRepo);
        assertThat(RepositoryRegistry.repository(Order.class)).isSameAs(threadRepo);

        ThreadContext.clear();

        assertThat(RepositoryRegistry.repository(Order.class)).isSameAs(globalRepo);
    }

    // ========================= Fixtures =========================

    /** \u9a8c\u8bc1<code>RepositoryRegistry.repository(TestAggregate.class)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>repository</code>\uff0c<code>RepositoryRegistry.repository(TestAggregate.class).findById(aggregate.id()).get()</code> \u7b49\u4e8e <code>aggregate</code>\uff0c<code>RepositoryRegistry.repository(TestAggregate.class)</code> \u629b\u51fa <code>BizRuntimeException.class</code>\u3002 */
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

    /** \u4f9b\u6d4b\u8bd5\u9a8c\u8bc1\u4e8b\u4ef6\u5e94\u7528\u548c\u6301\u4e45\u5316\u7684\u805a\u5408\u6837\u672c\u3002 */
    static final class Order extends AggregateRoot<String> {
        /** \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6\u3002 */
        private final String id;

        /**
         * \u8bbe\u7f6e <code>id</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>Order</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param id \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6
         */
        Order(String id) {
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
    }

    /** \u8ba2\u5355\u6d4b\u8bd5\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u6784\u9020\u4ed3\u50a8\u68c0\u7d22\u6761\u4ef6\u3002 */
    static final class OrderQuery extends Query<Order> {
    }

    /** \u63d0\u4f9b\u53ef\u63a7\u5236\u7684\u6570\u636e\u8bfb\u5199\u4ee5\u9a8c\u8bc1\u4ed3\u50a8\u8c03\u7528\u5951\u7ea6\u3002 */
    static class StubOrderRepository implements Repository<Order, Serializable> {
        /** \u5f53\u524d\u6d4b\u8bd5\u6a21\u578b\u7528\u4e8e\u5c55\u793a\u548c\u65ad\u8a00\u7684\u540d\u79f0\u3002 */
        final String name;

        /**
         * \u8bbe\u7f6e <code>name</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>StubOrderRepository</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
         */
        StubOrderRepository(String name) {
            this.name = name;
        }

        /**
         * \u901a\u8fc7 <code>Optional.of()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>Optional.of(new Order(String.valueOf(id)))</code>
         */
        @Override
        public Optional<Order> findById(Serializable id) {
            return Optional.of(new Order(String.valueOf(id)));
        }

        /** {@inheritDoc} */
        @Override
        public Order save(Order aggregate) {
            return aggregate;
        }

        /** {@inheritDoc} */
        @Override
        public void delete(Order aggregate) {
            // no-op
        }
    }

    /** \u6d4b\u8bd5\u4e13\u7528\u805a\u5408\uff0c\u4f9b\u9a8c\u8bc1\u805a\u5408\u6807\u8bc6\u548c\u4ed3\u50a8\u6216\u4e8b\u4ef6\u5904\u7406\u884c\u4e3a\u3002 */
    private static final class TestAggregate extends AggregateRoot<StringEntityId> {
        /** \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6\u3002 */
        private final StringEntityId id;

        /**
         * \u8bbe\u7f6e <code>id</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>TestAggregate</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param id \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6
         */
        private TestAggregate(StringEntityId id) {
            this.id = id;
        }

        /**
         * \u8bfb\u53d6<code>id</code> \u5b57\u6bb5\u3002
         * @return <code>id</code> \u5b57\u6bb5
         */
        @Override
        public StringEntityId id() {
            return id;
        }
    }

    /** \u63d0\u4f9b\u53ef\u63a7\u5236\u7684\u6570\u636e\u8bfb\u5199\u4ee5\u9a8c\u8bc1\u4ed3\u50a8\u8c03\u7528\u5951\u7ea6\u3002 */
    private static final class InMemoryRepository implements Repository<TestAggregate, StringEntityId> {
        /** \u5f53\u524d\u6d4b\u8bd5\u8bb0\u5f55\u7684\u5c5e\u6027\u503c\u6216\u6279\u91cf\u8f93\u5165\u96c6\u5408\u3002 */
        private final Map<StringEntityId, TestAggregate> values = new HashMap<StringEntityId, TestAggregate>();

        /**
         * \u901a\u8fc7 <code>Optional.ofNullable()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>Optional.ofNullable(values.get(id))</code>
         */
        @Override
        public Optional<TestAggregate> findById(StringEntityId id) {
            return Optional.ofNullable(values.get(id));
        }

        /** {@inheritDoc} */
        @Override
        public TestAggregate save(TestAggregate aggregate) {
            values.put(aggregate.id(), aggregate);
            return aggregate;
        }
    }
}
