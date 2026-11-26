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
package io.ddd4j.core.cqrs.readmodel.handler;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.StringEntityId;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ProjectionHandlerRegistry} 注册与路由契约测试。
 * <p>
 * 守护同名视图整批拒绝、多视图订阅和旧单值查询确定性；与命令单处理器规则独立。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
class ProjectionHandlerRegistryTest {

    private final ProjectionHandlerRegistry registry = new ProjectionHandlerRegistry();

    @Test
    void registeredHandlerIsFoundByExactEventType() {
        RecordingHandler handler = new RecordingHandler("order-summary",
                Set.of(OrderCreated.class, OrderPaid.class));

        registry.register(handler);

        assertSame(handler, registry.findHandler(OrderCreated.class).orElse(null),
                "findHandler should return the registered handler for the first subscribed type");
        assertSame(handler, registry.findHandler(OrderPaid.class).orElse(null),
                "findHandler should return the registered handler for the second subscribed type");
    }

    @Test
    void findHandlerReturnsEmptyForUnregisteredEventType() {
        registry.register(new RecordingHandler("order-summary", Set.of(OrderCreated.class)));

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
    void registerRejectsNullEventTypeBeforeRegisteringAnyType() {
        Set<Class<? extends DomainEvent<?>>> eventTypes = new LinkedHashSet<>();
        eventTypes.add(OrderPaid.class);
        eventTypes.add(null);

        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> registry.register(new RecordingHandler("invalid", eventTypes)));

        assertThat(ex).hasMessage("event type must not be null");
        assertThat(registry.all()).isEmpty();
        assertFalse(registry.findHandler(OrderPaid.class).isPresent());
    }

    @Test
    void duplicateViewRegistrationThrowsIllegalStateWithViewName() {
        registry.register(new RecordingHandler("order-summary", Set.of(OrderCreated.class)));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registry.register(new RecordingHandler("order-summary", Set.of(OrderCreated.class))));

        assertThat(ex).hasMessageContaining("order-summary");
    }

    @Test
    void conflictingBatchRegistrationIsRejectedEntirely() {
        registry.register(new RecordingHandler("order-summary", Set.of(OrderCreated.class)));
        RecordingHandler multiTypeHandler = new RecordingHandler("order-summary",
                Set.of(OrderCreated.class, OrderPaid.class));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registry.register(multiTypeHandler));

        assertThat(ex).hasMessageContaining("order-summary");
        assertFalse(registry.findHandler(OrderPaid.class).isPresent(),
                "non-conflicting types of the rejected handler must NOT be registered (all-or-nothing)");
        assertThat(registry.all())
                .hasSize(1)
                .doesNotContain(multiTypeHandler);
    }

    @Test
    void independentViewsCanSubscribeToSameEventAndLegacyLookupUsesFirst() {
        RecordingHandler first = new RecordingHandler("first", Set.of(OrderCreated.class));
        RecordingHandler second = new RecordingHandler("second", Set.of(OrderCreated.class));
        registry.register(first);
        registry.register(second);
        assertThat(registry.all()).containsExactly(first, second);
        assertThat(registry.findHandlers(OrderCreated.class)).containsExactly(first, second);
        assertThrows(UnsupportedOperationException.class,
                () -> registry.findHandlers(OrderCreated.class).clear());
        assertSame(first, registry.findHandler(OrderCreated.class).orElseThrow());
    }

    @Test
    void allViewIsImmutable() {
        registry.register(new RecordingHandler("order-summary", Set.of(OrderCreated.class)));

        assertThrows(UnsupportedOperationException.class,
                () -> registry.all().add(new RecordingHandler("another-view", Set.of(OrderPaid.class))));
    }

    @Test
    void multipleHandlersRouteToTheirOwnEventTypesAndMultiTypeHandlerAppearsOnce() {
        RecordingHandler summaryHandler = new RecordingHandler("order-summary", Set.of(OrderCreated.class));
        RecordingHandler paymentHandler = new RecordingHandler("payment-view", Set.of(OrderPaid.class));
        RecordingHandler lifecycleHandler = new RecordingHandler("order-lifecycle",
                Set.of(OrderShipped.class, OrderCancelled.class));

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
        ProjectionHandler handler = new RecordingHandler("order-summary", Set.of(OrderCreated.class));

        assertThat(handler.getName()).isEqualTo("order-summary");
        assertThat(handler.getCron()).isEqualTo("0/5 * * * * *");
        assertThat(handler.getChunkSize()).isEqualTo(100);
    }

    @Test
    void mutableHandlerDoesNotChangeRegisteredSubscriptionsOrLegacyIdentity() {
        MutableHandler handler = new MutableHandler();
        registry.register(handler);
        handler.types.clear();
        handler.types.add(OrderPaid.class);
        handler.name = "second";
        assertSame(handler, registry.findHandler(OrderCreated.class).orElseThrow());
        assertThat(registry.findHandlers(OrderCreated.class)).containsExactly(handler);
        assertThat(registry.findHandlers(OrderPaid.class)).isEmpty();
        assertThat(registry.all()).containsExactly(handler);
    }

    /**
     * 可变业务配置替身，用于验证登记快照与执行入口的一致性。
     */
    static final class MutableHandler implements ProjectionHandler {
        final Set<Class<? extends DomainEvent<?>>> types = new LinkedHashSet<>(Set.of(OrderCreated.class));
        final List<DomainEvent<?>> handled = new java.util.ArrayList<>();
        String name = "first";

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Collection<Class<? extends DomainEvent<?>>> eventTypes() {
            return types;
        }

        @Override
        public void handle(DomainEvent<?> event) {
            handled.add(event);
        }
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
            if (Objects.nonNull(failure)) {
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
