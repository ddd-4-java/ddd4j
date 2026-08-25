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

import io.ddd4j.sample.dropwizard.cqrs.cqrs.CommandBus;
import io.ddd4j.sample.dropwizard.cqrs.cqrs.InMemoryEventStore;
import io.ddd4j.sample.dropwizard.cqrs.cqrs.ViewManager;
import io.ddd4j.sample.dropwizard.cqrs.command.CreateOrderCommand;
import io.ddd4j.sample.dropwizard.cqrs.command.CreateOrderCommandHandler;
import io.ddd4j.sample.dropwizard.cqrs.readmodel.OrderSummaryView;
import io.ddd4j.sample.dropwizard.cqrs.repository.EventSourcingOrderRepository;
import io.ddd4j.sample.dropwizard.cqrs.web.OrderResource;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;

/**
 * Dropwizard CQRS 集成示例启动入口。
 */
public class DropwizardCqrsApplication extends Application<DropwizardCqrsConfiguration> {

    // 共享组件（手动装配）
    public static final InMemoryEventStore EVENT_STORE = new InMemoryEventStore();
    public static final EventSourcingOrderRepository ORDER_REPO = new EventSourcingOrderRepository(EVENT_STORE);
    public static final CreateOrderCommandHandler COMMAND_HANDLER = new CreateOrderCommandHandler(ORDER_REPO);
    public static final CommandBus COMMAND_BUS = createCommandBus();
    public static final OrderSummaryView READ_VIEW = new OrderSummaryView(ORDER_REPO);
    public static final ViewManager VIEW_MANAGER = createViewManager();

    private static CommandBus createCommandBus() {
        CommandBus bus = new CommandBus();
        bus.register(CreateOrderCommand.class, COMMAND_HANDLER::execute);
        return bus;
    }

    private static ViewManager createViewManager() {
        ViewManager mgr = new ViewManager(EVENT_STORE);
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
