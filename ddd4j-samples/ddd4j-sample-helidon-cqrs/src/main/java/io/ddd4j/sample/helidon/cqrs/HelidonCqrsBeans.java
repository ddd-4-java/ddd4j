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

import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.core.cqrs.eventstore.InMemoryEventStore;
import io.ddd4j.sample.helidon.cqrs.command.CreateOrderCommandHandler;
import io.ddd4j.sample.helidon.cqrs.readmodel.InMemoryEventChunkReader;
import io.ddd4j.sample.helidon.cqrs.readmodel.InMemoryViewManager;
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

import java.util.List;

/**
 * Helidon CDI 中 CQRS 组件的装配。
 *
 * <p>使用 core SPI（{@link DefaultCommandBus}、{@link InMemoryViewManager}）
 * 替代本地重写的 CommandBus/ViewManager。
 *
 * <p>{@link InMemoryEventStore}、{@code EventSourcingOrderRepository}、
 * {@code OrderSummaryView} 和 {@code InMemoryViewManager} 均为 {@code @ApplicationScoped}，
 * 保证所有注入点共享同一实例（读写模型状态一致）。
 *
 * <p>生命周期通过 {@code @Observes} 事件管理，避免 CDI 自引用循环依赖。
 */
@ApplicationScoped
public class HelidonCqrsBeans {

    @Inject
    private Instance<InMemoryViewManager> viewManagerInstance;

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
    InMemoryEventChunkReader chunkReader(InMemoryEventStore eventStore) {
        return new InMemoryEventChunkReader(eventStore);
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
        return new DefaultCommandBus(List.of(commandHandler));
    }

    @Produces
    @ApplicationScoped
    OrderSummaryView orderSummaryView(EventSourcingOrderRepository orderRepository) {
        return new OrderSummaryView(orderRepository);
    }

    @Produces
    @ApplicationScoped
    InMemoryViewManager viewManager(InMemoryEventChunkReader chunkReader, OrderSummaryView orderSummaryView) {
        InMemoryViewManager mgr = new InMemoryViewManager(chunkReader);
        mgr.register(orderSummaryView);
        return mgr;
    }
}
