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
package io.ddd4j.sample.vertx.cqrs;

import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.core.cqrs.eventstore.InMemoryEventStore;
import io.ddd4j.sample.vertx.cqrs.command.CreateOrderCommand;
import io.ddd4j.sample.vertx.cqrs.command.CreateOrderCommandHandler;
import io.ddd4j.sample.vertx.cqrs.readmodel.InMemoryEventChunkReader;
import io.ddd4j.sample.vertx.cqrs.readmodel.InMemoryViewManager;
import io.ddd4j.sample.vertx.cqrs.readmodel.OrderSummaryView;
import io.ddd4j.sample.vertx.cqrs.readmodel.OrderSummaryViewEntity;
import io.ddd4j.sample.vertx.cqrs.repository.EventSourcingOrderRepository;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.Collections;

/**
 * Vert.x CQRS 集成示例启动入口。
 */
public class VertxCqrsApplication {

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
    public static final CommandBus COMMAND_BUS = new DefaultCommandBus(Collections.singletonList(COMMAND_HANDLER));
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
     * 创建 CQRS HTTP 路由。
     *
     * @param vertx Vert.x 实例
     * @return 已注册的 HTTP 路由
     */
    public static Router createRouter(Vertx vertx) {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        // POST /orders -> 创建订单
        router.post("/orders").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            String orderNo = body.getString("orderNo");
            String buyerId = body.getString("buyerId");
            String buyerName = body.getString("buyerName");

            if (ORDER_REPO.findByOrderNo(orderNo).isPresent()) {
                ctx.response().setStatusCode(409)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"success\":false,\"message\":\"order already exists: " + orderNo + "\"}");
                return;
            }

            CreateOrderCommand command = new CreateOrderCommand(orderNo, buyerId, buyerName);
            Result<String> result = COMMAND_BUS.execute(command);

            ctx.response().setStatusCode(201)
                    .putHeader("Content-Type", "application/json")
                    .end("{\"success\":true,\"orderId\":\"" + result.getData() + "\"}");
        });

        // GET /orders/:id -> 查询订单摘要
        router.get("/orders/:id").handler(ctx -> {
            String id = ctx.pathParam("id");
            OrderSummaryViewEntity entity = READ_VIEW.findById(id);
            if (entity == null) {
                ctx.response().setStatusCode(404).end();
                return;
            }
            ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end("{\"orderId\":\"" + entity.getOrderId() +
                            "\",\"orderNo\":\"" + entity.getOrderNo() +
                            "\",\"buyerId\":\"" + entity.getBuyerId() +
                            "\",\"buyerName\":\"" + entity.getBuyerName() +
                            "\",\"status\":\"" + entity.getStatus() + "\"}");
        });

        return router;
    }

    /**
     * 启动 Vert.x CQRS 示例应用。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        Router router = createRouter(vertx);
        VIEW_MANAGER.start();
        vertx.createHttpServer().requestHandler(router).listen(8085)
                .onSuccess(server -> System.out.println("Vert.x CQRS server started on port " + server.actualPort()));
    }
}
