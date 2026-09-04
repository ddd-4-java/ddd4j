package io.ddd4j.core.ddd.model;

import java.util.Arrays;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityId;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.EventHandler;
import io.ddd4j.core.ddd.event.StringEntityType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AggregateRoot#apply(DomainEvent)} 与 {@link AggregateRoot#loadFromHistory(List)}
 * 的反射派发行为测试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
class AggregateRootApplyTest {

    @Test
    void applyInvokesAnnotatedHandlerAndRegistersEvent() {
        Order order = new Order("order-1");
        OrderCreatedEvent event = new OrderCreatedEvent();

        OrderCreatedEvent applied = order.apply(event);

        assertSame(event, applied, "apply should return the applied event");
        assertEquals("CREATED", order.status, "@EventHandler method should mutate aggregate state");
        assertTrue(order.domainEvents().contains(event), "applied event should be registered in domainEvents");
    }

    @Test
    void applyThrowsWhenNoHandlerRegistered() {
        Order order = new Order("order-1");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> order.apply(new UnhandledEvent()));

        assertTrue(exception.getMessage().contains(UnhandledEvent.class.getName()),
                "message should name the event type");
        assertTrue(exception.getMessage().contains(Order.class.getName()),
                "message should name the aggregate type");
    }

    @Test
    void loadFromHistoryRebuildsAggregateWithoutEnqueueing() {
        Order order = new Order("order-1");

        order.loadFromHistory(Arrays.asList(new OrderCreatedEvent(), new OrderCreatedEvent()));

        assertEquals("CREATED", order.status, "replay should rebuild aggregate state");
        assertTrue(order.domainEvents().isEmpty(), "replay must not enqueue events into domainEvents");
        assertFalse(order.hasDomainEvents());
    }

    @Test
    void ignoreOnReplayHandlerRunsOnApplyButSkippedOnReplay() {
        Order applying = new Order("order-1");
        applying.apply(new OrderNotifiedEvent());
        assertTrue(applying.notified, "apply should invoke ignoreOnReplay=true handler");

        Order replaying = new Order("order-1");
        replaying.loadFromHistory(Arrays.asList(new OrderCreatedEvent(), new OrderNotifiedEvent()));
        assertEquals("CREATED", replaying.status, "normal handlers should still replay");
        assertFalse(replaying.notified, "loadFromHistory should skip ignoreOnReplay=true handler");
    }

    @Test
    void loadFromHistoryIsNullSafe() {
        Order order = new Order("order-1");

        order.loadFromHistory(null);

        assertNull(order.status);
        assertTrue(order.domainEvents().isEmpty());
    }

    /**
     * 测试聚合：满足 {@link Entity} 契约（实现 {@code id()}），提供两个处理器
     * （一个普通、一个 {@code ignoreOnReplay=true}）。
     */
    static class Order extends AggregateRoot<String> {

        private final String orderId;

        String status;

        boolean notified;

        Order(String orderId) {
            this.orderId = orderId;
        }

        @Override
        public String id() {
            return orderId;
        }

        @EventHandler
        void on(OrderCreatedEvent event) {
            this.status = "CREATED";
        }

        @EventHandler(ignoreOnReplay = true)
        void on(OrderNotifiedEvent event) {
            this.notified = true;
        }
    }

    static class OrderCreatedEvent extends DomainEvent<OrderCreatedEvent.OrderId> {

        OrderCreatedEvent() {
            super(new EntityIdPath(new OrderId("order-1")));
        }

        record OrderId(String value) implements EntityId {

            private static final EntityType TYPE = new StringEntityType("Order");

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
    }

    static class OrderNotifiedEvent extends DomainEvent<OrderCreatedEvent.OrderId> {

        OrderNotifiedEvent() {
            super(new EntityIdPath(new OrderCreatedEvent.OrderId("order-1")));
        }
    }

    static class UnhandledEvent extends DomainEvent<OrderCreatedEvent.OrderId> {

        UnhandledEvent() {
            super(new EntityIdPath(new OrderCreatedEvent.OrderId("order-1")));
        }
    }
}
