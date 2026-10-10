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
import io.ddd4j.sample.vertx.cqrs.repository.EventSourcingOrderRepository;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.List;

/**
 * Vert.x CQRS 集成示例启动入口。
 */
public class VertxCqrsApplication {

    // 共享组件（手动装配，使用 core SPI）

    /** 内存事件存储，示例共享组件。 */
    public static final InMemoryEventStore EVENT_STORE = new InMemoryEventStore();
    /** 基于事件溯源的订单仓储。 */
    public static final EventSourcingOrderRepository ORDER_REPO = new EventSourcingOrderRepository(EVENT_STORE);
    /** 创建订单命令处理器。 */
    public static final CreateOrderCommandHandler COMMAND_HANDLER = new CreateOrderCommandHandler(ORDER_REPO);
    /** 默认命令总线，注册全部命令处理器。 */
    public static final CommandBus COMMAND_BUS = new DefaultCommandBus(List.of(COMMAND_HANDLER));
    /** 订单摘要读视图。 */
    public static final OrderSummaryView READ_VIEW = new OrderSummaryView(ORDER_REPO);
    /** 内存事件分块读取器，供读模型重建使用。 */
    public static final InMemoryEventChunkReader CHUNK_READER = new InMemoryEventChunkReader(EVENT_STORE);
    /** 读模型管理器，负责注册并启动读视图的事件订阅。 */
    public static final InMemoryViewManager VIEW_MANAGER = createViewManager();

    private static InMemoryViewManager createViewManager() {
        InMemoryViewManager mgr = new InMemoryViewManager(CHUNK_READER);
        mgr.register(READ_VIEW);
        return mgr;
    }

    /**
     * 创建对象。
     * @param vertx vertx 参数。
     * @return 方法执行结果。
     */
    public static Router createRouter(Vertx vertx) {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        // POST /orders -> 创建订单
        router.post("/orders").handler(ctx -> {
            var body = ctx.body().asJsonObject();
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
            var entity = READ_VIEW.findById(id);
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
     * main() 方法。
     * @param args 命令行参数。
     */
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        Router router = createRouter(vertx);
        VIEW_MANAGER.start();
        vertx.createHttpServer().requestHandler(router).listen(8085)
                .onSuccess(server -> System.out.println("Vert.x CQRS server started on port " + server.actualPort()));
    }
}
