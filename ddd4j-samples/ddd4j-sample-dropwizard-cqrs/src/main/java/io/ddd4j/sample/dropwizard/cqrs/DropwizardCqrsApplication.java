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

import io.ddd4j.dropwizard.Ddd4jLogbackAccessRequestLogFactory;
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
import java.util.Objects;

/**
 * Dropwizard CQRS 集成示例启动入口。
 */
public class DropwizardCqrsApplication extends Application<DropwizardCqrsConfiguration> {
    private io.ddd4j.sample.dropwizard.cqrs.persistent.PersistentOrders persistent;
    private javax.sql.DataSource suppliedSource;
    /** 默认使用 YAML 或系统属性中的数据库配置。 */
    public DropwizardCqrsApplication() { }
    /** @param source 调用方拥有的已迁移数据源；与 main 共用其余全部装配 */
    public DropwizardCqrsApplication(javax.sql.DataSource source) { this.suppliedSource = source; }
    /** @return 已装配的持久场景，demo-memory 模式为空 */
    public io.ddd4j.sample.dropwizard.cqrs.persistent.PersistentOrders persistent() { return persistent; }


    // 共享组件（手动装配，使用 core SPI）
    /**
     * 事件存储。
     */
    public static final InMemoryEventStore EVENT_STORE = new InMemoryEventStore();
    /**
     * 订单仓储。
     */
    public static final EventSourcingOrderRepository ORDER_REPO = new EventSourcingOrderRepository(EVENT_STORE);
    /**
     * 创建订单命令处理器。
     */
    public static final CreateOrderCommandHandler COMMAND_HANDLER = new CreateOrderCommandHandler(ORDER_REPO);
    /**
     * 命令总线。
     */
    public static final CommandBus COMMAND_BUS = new DefaultCommandBus(List.of(COMMAND_HANDLER));
    /**
     * 订单读取视图。
     */
    public static final OrderSummaryView READ_VIEW = new OrderSummaryView(ORDER_REPO);
    /**
     * 事件块读取器。
     */
    public static final InMemoryEventChunkReader CHUNK_READER = new InMemoryEventChunkReader(EVENT_STORE);
    /**
     * 投影视图管理器。
     */
    public static final InMemoryViewManager VIEW_MANAGER = createViewManager();

    private static InMemoryViewManager createViewManager() {
        InMemoryViewManager mgr = new InMemoryViewManager(CHUNK_READER);
        mgr.register(READ_VIEW);
        return mgr;
    }

    /**
     * 启动 Dropwizard CQRS 示例应用。
     *
     *
 * @param args 命令行参数
     *
 * @throws Exception 应用启动失败时
     */
    public static void main(String[] args) throws Exception {
        new DropwizardCqrsApplication().run(args);
    }

    @Override
    public void initialize(Bootstrap<DropwizardCqrsConfiguration> bootstrap) {
        bootstrap.addBundle(new io.dropwizard.core.ConfiguredBundle<DropwizardCqrsConfiguration>() {
            @Override public void run(DropwizardCqrsConfiguration configuration, Environment environment) {
                javax.sql.DataSource source = suppliedSource;
                if (Objects.isNull(source) && io.ddd4j.kit.lang.StrKit.isNotBlank(configuration.getJdbcUrl())) {
                    var postgres = new org.postgresql.ds.PGSimpleDataSource();
                    postgres.setUrl(configuration.getJdbcUrl()); postgres.setUser(configuration.getJdbcUser());
                    postgres.setPassword(configuration.getJdbcPassword()); source = postgres;
                }
                if (Objects.nonNull(source)) {
                    persistent = new io.ddd4j.sample.dropwizard.cqrs.persistent.PersistentOrders(source);
                    environment.lifecycle().manage(persistent);
                    persistent.bundle().run(configuration, environment);
                } else {
                    persistent = null;
                    new io.ddd4j.dropwizard.Ddd4jBundle<DropwizardCqrsConfiguration>().run(configuration, environment);
                }
            }
        });
        bootstrap.addBundle(new io.ddd4j.web.dropwizard.Ddd4jDropwizardWebBundle<DropwizardCqrsConfiguration>(configuration -> {
            var web = new io.ddd4j.web.dropwizard.Ddd4jDropwizardWebConfiguration();
            web.setDefaultAuthenticationMode(io.ddd4j.web.core.auth.AuthenticationMode.DISABLED);
            web.setIdempotencyEnabled(false); return web;
        }));
    }

    @Override
    public void run(DropwizardCqrsConfiguration configuration, Environment environment) {
        Ddd4jLogbackAccessRequestLogFactory.configure(configuration);
        environment.jersey().register(new OrderResource());
        if (Objects.nonNull(persistent)) { environment.jersey().register(persistent.resource()); }
        // 旧静态 API 和内存 DTO 保留；原生 Managed 为旧调度配对停止。
        environment.lifecycle().manage(new io.dropwizard.lifecycle.Managed() {
            @Override public void start() { VIEW_MANAGER.start(); }
            @Override public void stop() { VIEW_MANAGER.stop(); }
        });
    }
}
