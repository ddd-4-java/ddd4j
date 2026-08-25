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
package io.ddd4j.sample.micronaut.cqrs;

import io.ddd4j.sample.micronaut.cqrs.cqrs.CommandBus;
import io.ddd4j.sample.micronaut.cqrs.cqrs.ViewManager;
import io.ddd4j.sample.micronaut.cqrs.command.CreateOrderCommand;
import io.ddd4j.sample.micronaut.cqrs.command.CreateOrderCommandHandler;
import io.ddd4j.sample.micronaut.cqrs.readmodel.OrderSummaryView;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.Micronaut;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;

/**
 * Micronaut CQRS 集成示例启动入口。
 *
 * <p>装配 CommandBus / ViewManager，注册命令处理器和投影视图。
 */
public class MicronautCqrsApplication {

    public static void main(String[] args) {
        Micronaut.run(MicronautCqrsApplication.class, args);
    }

    @Factory
    public static class CqrsConfig {

        @Singleton
        io.ddd4j.core.cqrs.eventstore.InMemoryEventStore eventStore() {
            return new io.ddd4j.core.cqrs.eventstore.InMemoryEventStore();
        }

        @Singleton
        CommandBus commandBus(CreateOrderCommandHandler handler) {
            CommandBus bus = new CommandBus();
            bus.register(CreateOrderCommand.class, handler::execute);
            return bus;
        }

        @Singleton
        ViewManager viewManager(io.ddd4j.core.cqrs.eventstore.InMemoryEventStore eventStore,
                                OrderSummaryView orderSummaryView) {
            ViewManager manager = new ViewManager(eventStore);
            manager.register(orderSummaryView);
            return manager;
        }
    }

    @Singleton
    static class StartupListener {

        private final ViewManager viewManager;

        StartupListener(ViewManager viewManager) {
            this.viewManager = viewManager;
        }

        @EventListener
        void onStartup(StartupEvent event) {
            viewManager.start();
        }
    }
}
