package io.ddd4j.core.ddd.repository;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.exception.BizRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RepositoryRegistry} tests.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class RepositoryRegistryTest {

    private StubOrderRepository globalRepo;
    private StubOrderRepository threadRepo;

    @BeforeEach
    void setUp() {
        BaseContext.clear();
        ThreadContext.clear();
        RepositoryRegistry.unregister(Order.class);
        RepositoryRegistry.unregisterQuery(OrderQuery.class);
        globalRepo = new StubOrderRepository("global");
        threadRepo = new StubOrderRepository("thread");
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
        ThreadContext.clear();
        RepositoryRegistry.unregister(Order.class);
        RepositoryRegistry.unregisterQuery(OrderQuery.class);
    }

    @Test
    void key_shouldUsePrefixWithClassName() {
        assertThat(RepositoryRegistry.key(Order.class))
                .isEqualTo("ddd4j.repository." + Order.class.getName());
    }

    @Test
    void register_and_repository_shouldReturnSameInstance() {
        RepositoryRegistry.register(Order.class, globalRepo);

        Repository<Order, ?> resolved = RepositoryRegistry.repository(Order.class);

        assertThat(resolved).isSameAs(globalRepo);
    }

    @Test
    void repository_shouldThrowBizRuntimeExceptionWhenNotRegistered() {
        assertThatThrownBy(() -> RepositoryRegistry.repository(Order.class))
                .isInstanceOf(BizRuntimeException.class)
                .hasMessageContaining("Repository not found for aggregate Order")
                .hasMessageContaining("Register it via RepositoryRegistry.register(Order, repository)");
    }

    @Test
    void repositoryForQuery_shouldThrowWhenNotRegistered() {
        assertThatThrownBy(() -> RepositoryRegistry.repositoryForQuery(OrderQuery.class))
                .isInstanceOf(BizRuntimeException.class)
                .hasMessageContaining("Repository not found for query OrderQuery")
                .hasMessageContaining("Register it via RepositoryRegistry.register(modelClass, OrderQuery, repository)");
    }

    @Test
    void registerWithQuery_shouldResolveBothAggregateAndQuery() {
        RepositoryRegistry.register(Order.class, OrderQuery.class, globalRepo);

        assertThat(RepositoryRegistry.repository(Order.class)).isSameAs(globalRepo);
        assertThat(RepositoryRegistry.repositoryForQuery(OrderQuery.class)).isSameAs(globalRepo);
    }

    @Test
    void threadContext_shouldOverrideBaseContext() {
        RepositoryRegistry.register(Order.class, globalRepo);
        ThreadContext.inject(RepositoryRegistry.key(Order.class), Repository.class, threadRepo);

        Repository<Order, ?> resolved = RepositoryRegistry.repository(Order.class);

        assertThat(resolved).isSameAs(threadRepo);
    }

    @Test
    void unregister_shouldRemoveFromContextAndStaticMap() {
        RepositoryRegistry.register(Order.class, globalRepo);
        assertThat(RepositoryRegistry.repository(Order.class)).isSameAs(globalRepo);

        RepositoryRegistry.unregister(Order.class);

        assertThatThrownBy(() -> RepositoryRegistry.repository(Order.class))
                .isInstanceOf(BizRuntimeException.class);
    }

    @Test
    void unregisterQuery_shouldRemoveQueryMapping() {
        RepositoryRegistry.register(Order.class, OrderQuery.class, globalRepo);
        assertThat(RepositoryRegistry.repositoryForQuery(OrderQuery.class)).isSameAs(globalRepo);

        RepositoryRegistry.unregisterQuery(OrderQuery.class);

        assertThatThrownBy(() -> RepositoryRegistry.repositoryForQuery(OrderQuery.class))
                .isInstanceOf(BizRuntimeException.class);
    }

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

    @Test
    void repository_shouldFallbackToBaseContextAfterThreadRemoved() {
        RepositoryRegistry.register(Order.class, globalRepo);
        ThreadContext.inject(RepositoryRegistry.key(Order.class), Repository.class, threadRepo);
        assertThat(RepositoryRegistry.repository(Order.class)).isSameAs(threadRepo);

        ThreadContext.clear();

        assertThat(RepositoryRegistry.repository(Order.class)).isSameAs(globalRepo);
    }

    // ========================= Fixtures =========================

    static final class Order extends AggregateRoot<String> {
        private final String id;

        Order(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    static final class OrderQuery extends Query<Order> {
    }

    static class StubOrderRepository implements Repository<Order, Serializable> {
        final String name;

        StubOrderRepository(String name) {
            this.name = name;
        }

        @Override
        public Optional<Order> findById(Serializable id) {
            return Optional.of(new Order(String.valueOf(id)));
        }

        @Override
        public Order save(Order aggregate) {
            return aggregate;
        }

        @Override
        public void delete(Order aggregate) {
            // no-op
        }
    }
}
