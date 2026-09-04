package io.ddd4j.core.ddd.model;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Extended tests for {@link AggregateRoot} event management and rich methods.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class AggregateRootEventsTest {

    @BeforeEach
    void setUp() {
        BaseContext.clear();
        ThreadContext.clear();
        RepositoryRegistry.unregister(Order.class);
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
        ThreadContext.clear();
        RepositoryRegistry.unregister(Order.class);
    }

    @Test
    void registerEvent_shouldExposeEventViaDomainEvents() {
        Order order = new Order("o1");
        order.markCreated();

        assertThat(order.hasDomainEvents()).isTrue();
        assertThat(order.domainEvents()).hasSize(1);
        assertThat(order.domainEvents().get(0)).isInstanceOf(TestEvent.class);
        assertThat(((TestEvent) order.domainEvents().get(0)).getName()).isEqualTo("created");
    }

    @Test
    void domainEvents_shouldBeImmutableView() {
        Order order = new Order("o1");
        order.markCreated();

        List<DomainEvent<?>> events = order.domainEvents();

        assertThatThrownBy(() -> events.add(new TestEvent("x")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void pullDomainEvents_shouldReturnAndClearBuffer() {
        Order order = new Order("o1");
        order.markCreated();

        List<DomainEvent<?>> pulled = order.pullDomainEvents();

        assertThat(pulled).hasSize(1);
        assertThat(order.hasDomainEvents()).isFalse();
    }

    @Test
    void clearDomainEvents_shouldEmptyBuffer() {
        Order order = new Order("o1");
        order.markCreated();

        order.clearDomainEvents();

        assertThat(order.hasDomainEvents()).isFalse();
        assertThat(order.domainEvents()).isEmpty();
    }

    @Test
    void registerEvent_shouldThrowNpeForNullEvent() {
        Order order = new Order("o1");

        assertThatThrownBy(() -> order.raiseNullEvent())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void hasDomainEvents_shouldBeFalseInitially() {
        Order order = new Order("o1");

        assertThat(order.hasDomainEvents()).isFalse();
    }

    @Test
    void registerEvent_shouldBufferMultipleEventsInOrder() {
        Order order = new Order("o1");
        order.markCreated();
        order.rename("paid");

        List<DomainEvent<?>> events = order.domainEvents();

        assertThat(events).hasSize(2);
        assertThat(((TestEvent) events.get(0)).getName()).isEqualTo("created");
        assertThat(((TestEvent) events.get(1)).getName()).isEqualTo("paid");
    }

    @Test
    void save_shouldDelegateToRegisteredRepository() {
        RecordingRepository repo = new RecordingRepository();
        RepositoryRegistry.register(Order.class, repo);
        Order order = new Order("o1");

        Order saved = order.save();

        assertThat(repo.saved).isSameAs(order);
        assertThat(saved).isSameAs(order);
    }

    @Test
    void delete_shouldDelegateToRegisteredRepository() {
        RecordingRepository repo = new RecordingRepository();
        RepositoryRegistry.register(Order.class, repo);
        Order order = new Order("o1");

        order.delete();

        assertThat(repo.deleted).isSameAs(order);
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

        void markCreated() {
            registerEvent(new TestEvent("created"));
        }

        void rename(String name) {
            registerEvent(new TestEvent(name));
        }

        void raiseNullEvent() {
            registerEvent(null);
        }
    }

    static final class TestEvent extends DomainEvent<OrderId> {

        private final String name;

        TestEvent(String name) {
            super(new EntityIdPath(new OrderId(name)));
            this.name = name;
        }

        String getName() {
            return name;
        }

    }

    static final class OrderId implements AggregateRootId {

        private static final EntityType TYPE = new OrderEntityType();

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

    static final class OrderEntityType implements EntityType {

        @Override
        public String asString() {
            return "Order";
        }
    }

    static class RecordingRepository implements Repository<Order, Serializable> {
        Order saved;
        Order deleted;

        @Override
        public Optional<Order> findById(Serializable id) {
            return Optional.of(new Order(String.valueOf(id)));
        }

        @Override
        public Order save(Order aggregate) {
            this.saved = aggregate;
            return aggregate;
        }

        @Override
        public void delete(Order aggregate) {
            this.deleted = aggregate;
        }
    }
}
