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

import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.core.cqrs.eventstore.InMemoryEventStore;
import io.ddd4j.sample.micronaut.cqrs.command.CreateOrderCommandHandler;
import io.ddd4j.sample.micronaut.cqrs.readmodel.InMemoryEventChunkReader;
import io.ddd4j.sample.micronaut.cqrs.readmodel.InMemoryViewManager;
import io.ddd4j.sample.micronaut.cqrs.readmodel.OrderSummaryView;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.Micronaut;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Micronaut CQRS 集成示例启动入口。
 *
 * <p>装配 core SPI CommandBus / InMemoryViewManager。
 */
public class MicronautCqrsApplication {

    public static void main(String[] args) {
        Micronaut.run(MicronautCqrsApplication.class, args);
    }

    @Factory
    public static class CqrsConfig {

        @Singleton
        InMemoryEventStore eventStore() {
            return new InMemoryEventStore();
        }

        @Singleton
        InMemoryEventChunkReader chunkReader(InMemoryEventStore eventStore) {
            return new InMemoryEventChunkReader(eventStore);
        }

        @Singleton
        CommandBus commandBus(CreateOrderCommandHandler handler) {
            return new DefaultCommandBus(List.of(handler));
        }

        @Singleton
        InMemoryViewManager viewManager(InMemoryEventChunkReader chunkReader,
                                        OrderSummaryView orderSummaryView) {
            InMemoryViewManager manager = new InMemoryViewManager(chunkReader);
            manager.register(orderSummaryView);
            return manager;
        }
    }

    @Singleton
    static class StartupListener {

        private final InMemoryViewManager viewManager;

        StartupListener(InMemoryViewManager viewManager) {
            this.viewManager = viewManager;
        }

        @EventListener
        void onStartup(StartupEvent event) {
            viewManager.start();
        }
    }
}
