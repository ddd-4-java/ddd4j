package io.ddd4j.data.projection;

import java.util.Collections;
import java.util.Arrays;
import java.util.LinkedHashSet;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.StringEntityId;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ProjectionHandlerRegistry} 注册与路由契约测试。
 * <p>
 * 重点守护：register 的 <b>整批拒绝</b>（all-or-nothing）语义——多类型 handler
 * 任一事件类型冲突时，全部类型均不落库，不产生半注册状态；未注册类型的
 * {@code findHandler} 返回 {@link Optional#empty()}（兜底策略归
 * {@link ProjectionDispatcher}）。语义与 ddd4j-data-cqrs 的
 * {@code CommandRegistry} 同源（ADR-0004）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
class ProjectionHandlerRegistryTest {

    private final ProjectionHandlerRegistry registry = new ProjectionHandlerRegistry();

    @Test
    void registeredHandlerIsFoundByExactEventType() {
        RecordingHandler handler = new RecordingHandler("order-summary",
                Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(OrderCreated.class, OrderPaid.class)));

        registry.register(handler);

        assertSame(handler, registry.findHandler(OrderCreated.class).orElse(null),
                "findHandler should return the registered handler for the first subscribed type");
        assertSame(handler, registry.findHandler(OrderPaid.class).orElse(null),
                "findHandler should return the registered handler for the second subscribed type");
    }

    @Test
    void findHandlerReturnsEmptyForUnregisteredEventType() {
        registry.register(new RecordingHandler("order-summary", Collections.singleton(OrderCreated.class)));

        Optional<ProjectionHandler> found = registry.findHandler(OrderPaid.class);

        assertFalse(found.isPresent(),
                "unregistered event type should yield Optional.empty() (dispatcher owns the fallback strategy)");
    }

    @Test
    void findHandlerRejectsNullEventType() {
        assertThrows(NullPointerException.class, () -> registry.findHandler(null));
    }

    @Test
    void registerRejectsNullHandler() {
        assertThrows(NullPointerException.class, () -> registry.register(null));
    }

    @Test
    void duplicateEventTypeRegistrationThrowsIllegalStateWithEventName() {
        registry.register(new RecordingHandler("order-summary", Collections.singleton(OrderCreated.class)));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registry.register(new RecordingHandler("another-view", Collections.singleton(OrderCreated.class)));

        assertThat(ex).hasMessageContaining(OrderCreated.class.getName());
    }

    @Test
    void conflictingBatchRegistrationIsRejectedEntirely() {
        registry.register(new RecordingHandler("order-summary", Collections.singleton(OrderCreated.class)));
        RecordingHandler multiTypeHandler = new RecordingHandler("order-lifecycle",
                Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(OrderCreated.class, OrderPaid.class)));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registry.register(multiTypeHandler));

        assertThat(ex).hasMessageContaining(OrderCreated.class.getName());
        assertFalse(registry.findHandler(OrderPaid.class).isPresent(),
                "non-conflicting types of the rejected handler must NOT be registered (all-or-nothing)");
        assertThat(registry.all())
                .hasSize(1)
                .doesNotContain(multiTypeHandler);
    }

    @Test
    void allViewIsImmutable() {
        registry.register(new RecordingHandler("order-summary", Collections.singleton(OrderCreated.class)));

        assertThrows(UnsupportedOperationException.class,
                () -> registry.all().add(new RecordingHandler("another-view", Collections.singleton(OrderPaid.class)));
    }

    @Test
    void multipleHandlersRouteToTheirOwnEventTypesAndMultiTypeHandlerAppearsOnce() {
        RecordingHandler summaryHandler = new RecordingHandler("order-summary", Collections.singleton(OrderCreated.class));
        RecordingHandler paymentHandler = new RecordingHandler("payment-view", Collections.singleton(OrderPaid.class));
        RecordingHandler lifecycleHandler = new RecordingHandler("order-lifecycle",
                Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(OrderShipped.class, OrderCancelled.class)));

        registry.register(summaryHandler);
        registry.register(paymentHandler);
        registry.register(lifecycleHandler);

        assertSame(summaryHandler, registry.findHandler(OrderCreated.class).orElse(null));
        assertSame(paymentHandler, registry.findHandler(OrderPaid.class).orElse(null));
        assertSame(lifecycleHandler, registry.findHandler(OrderShipped.class).orElse(null));
        assertSame(lifecycleHandler, registry.findHandler(OrderCancelled.class).orElse(null));
        assertThat(registry.all())
                .hasSize(3)
                .containsOnlyOnce(lifecycleHandler);
    }

    @Test
    void defaultHandlerConfigurationMatchesContract() {
        ProjectionHandler handler = new RecordingHandler("order-summary", Collections.singleton(OrderCreated.class));

        assertThat(handler.getName()).isEqualTo("order-summary");
        assertThat(handler.getCron()).isEqualTo("0/5 * * * * *");
        assertThat(handler.getChunkSize()).isEqualTo(100);
    }

    /**
     * 记录型 handler 测试替身（可注入失败）。
     */
    static final class RecordingHandler implements ProjectionHandler {

        private final String name;

        private final Set<Class<? extends DomainEvent<?>>> eventTypes;

        private final List<DomainEvent<?>> handled = new java.util.ArrayList<>();

        private RuntimeException failure;

        RecordingHandler(String name, Set<Class<? extends DomainEvent<?>>> eventTypes) {
            this.name = name;
            this.eventTypes = eventTypes;
        }

        void failWith(RuntimeException failure) {
            this.failure = failure;
        }

        List<DomainEvent<?>> handled() {
            return handled;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Collection<Class<? extends DomainEvent<?>>> eventTypes() {
            return eventTypes;
        }

        @Override
        public void handle(DomainEvent<?> event) {
            if (failure != null) {
                throw failure;
            }
            handled.add(event);
        }
    }

    static final class OrderCreated extends DomainEvent<StringEntityId> {

        OrderCreated() {
            super("order-1");
        }
    }

    static final class OrderPaid extends DomainEvent<StringEntityId> {

        OrderPaid() {
            super("order-1");
        }
    }

    static final class OrderShipped extends DomainEvent<StringEntityId> {

        OrderShipped() {
            super("order-1");
        }
    }

    static final class OrderCancelled extends DomainEvent<StringEntityId> {

        OrderCancelled() {
            super("order-1");
        }
    }
}
