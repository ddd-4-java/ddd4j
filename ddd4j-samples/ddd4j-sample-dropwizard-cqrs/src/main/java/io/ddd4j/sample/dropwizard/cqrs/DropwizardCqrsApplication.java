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
package io.ddd4j.sample.dropwizard.cqrs;

import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.core.cqrs.eventstore.InMemoryEventStore;
import io.ddd4j.sample.dropwizard.cqrs.command.CreateOrderCommandHandler;
import io.ddd4j.sample.dropwizard.cqrs.readmodel.InMemoryEventChunkReader;
import io.ddd4j.sample.dropwizard.cqrs.readmodel.InMemoryViewManager;
import io.ddd4j.sample.dropwizard.cqrs.readmodel.OrderSummaryView;
import io.ddd4j.sample.dropwizard.cqrs.repository.EventSourcingOrderRepository;
import io.ddd4j.sample.dropwizard.cqrs.web.OrderResource;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;

import java.util.List;

/**
 * Dropwizard CQRS 集成示例启动入口。
 */
public class DropwizardCqrsApplication extends Application<DropwizardCqrsConfiguration> {

    // 共享组件（手动装配，使用 core SPI）
    public static final InMemoryEventStore EVENT_STORE = new InMemoryEventStore();
    public static final EventSourcingOrderRepository ORDER_REPO = new EventSourcingOrderRepository(EVENT_STORE);
    public static final CreateOrderCommandHandler COMMAND_HANDLER = new CreateOrderCommandHandler(ORDER_REPO);
    public static final CommandBus COMMAND_BUS = new DefaultCommandBus(List.of(COMMAND_HANDLER));
    public static final OrderSummaryView READ_VIEW = new OrderSummaryView(ORDER_REPO);
    public static final InMemoryEventChunkReader CHUNK_READER = new InMemoryEventChunkReader(EVENT_STORE);
    public static final InMemoryViewManager VIEW_MANAGER = createViewManager();

    private static InMemoryViewManager createViewManager() {
        InMemoryViewManager mgr = new InMemoryViewManager(CHUNK_READER);
        mgr.register(READ_VIEW);
        return mgr;
    }

    public static void main(String[] args) throws Exception {
        new DropwizardCqrsApplication().run(args);
    }

    @Override
    public void initialize(Bootstrap<DropwizardCqrsConfiguration> bootstrap) {
    }

    @Override
    public void run(DropwizardCqrsConfiguration configuration, Environment environment) {
        environment.jersey().register(new OrderResource());
        VIEW_MANAGER.start();
    }
}
