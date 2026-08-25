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
package io.ddd4j.sample.helidon.cqrs;

import io.ddd4j.sample.helidon.cqrs.command.CreateOrderCommand;
import io.ddd4j.sample.helidon.cqrs.command.CreateOrderCommandHandler;
import io.ddd4j.sample.helidon.cqrs.cqrs.CommandBus;
import io.ddd4j.sample.helidon.cqrs.cqrs.InMemoryEventStore;
import io.ddd4j.sample.helidon.cqrs.cqrs.ViewManager;
import io.ddd4j.sample.helidon.cqrs.readmodel.OrderSummaryView;
import io.ddd4j.sample.helidon.cqrs.repository.EventSourcingOrderRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.BeforeDestroyed;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * Helidon CDI 中 CQRS 组件的装配。
 *
 * <p>使用 {@code @Produces} 工厂方法代替手动静态字段，
 * 使 OrderResource 可以通过 {@code @Inject} 获取依赖。
 *
 * <p>{@code InMemoryEventStore}、{@code EventSourcingOrderRepository}、
 * {@code OrderSummaryView} 和 {@code ViewManager} 均为 {@code @ApplicationScoped}，
 * 保证所有注入点共享同一实例（读写模型状态一致）。
 *
 * <p>生命周期通过 {@code @Observes} 事件管理，避免 CDI 自引用循环依赖。
 */
@ApplicationScoped
public class HelidonCqrsBeans {

    @Inject
    private Instance<ViewManager> viewManagerInstance;

    void onStart(@Observes @Initialized(ApplicationScoped.class) Object event) {
        viewManagerInstance.get().start();
    }

    void onStop(@Observes @BeforeDestroyed(ApplicationScoped.class) Object event) {
        viewManagerInstance.get().stop();
    }

    @Produces
    @ApplicationScoped
    InMemoryEventStore eventStore() {
        return new InMemoryEventStore();
    }

    @Produces
    @ApplicationScoped
    EventSourcingOrderRepository orderRepository(InMemoryEventStore eventStore) {
        return new EventSourcingOrderRepository(eventStore);
    }

    @Produces
    @Dependent
    CreateOrderCommandHandler commandHandler(EventSourcingOrderRepository orderRepository) {
        return new CreateOrderCommandHandler(orderRepository);
    }

    @Produces
    @ApplicationScoped
    CommandBus commandBus(CreateOrderCommandHandler commandHandler) {
        CommandBus bus = new CommandBus();
        bus.register(CreateOrderCommand.class, commandHandler::execute);
        return bus;
    }

    @Produces
    @ApplicationScoped
    OrderSummaryView orderSummaryView(EventSourcingOrderRepository orderRepository) {
        return new OrderSummaryView(orderRepository);
    }

    @Produces
    @ApplicationScoped
    ViewManager viewManager(InMemoryEventStore eventStore, OrderSummaryView orderSummaryView) {
        ViewManager mgr = new ViewManager(eventStore);
        mgr.register(orderSummaryView);
        return mgr;
    }
}
