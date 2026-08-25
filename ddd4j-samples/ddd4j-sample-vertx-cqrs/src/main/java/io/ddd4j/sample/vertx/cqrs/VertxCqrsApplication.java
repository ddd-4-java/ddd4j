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

import io.ddd4j.sample.vertx.cqrs.cqrs.CommandBus;
import io.ddd4j.core.cqrs.eventstore.InMemoryEventStore;
import io.ddd4j.sample.vertx.cqrs.cqrs.ViewManager;
import io.ddd4j.sample.vertx.cqrs.command.CreateOrderCommand;
import io.ddd4j.sample.vertx.cqrs.command.CreateOrderCommandHandler;
import io.ddd4j.sample.vertx.cqrs.readmodel.OrderSummaryView;
import io.ddd4j.sample.vertx.cqrs.repository.EventSourcingOrderRepository;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * Vert.x CQRS 集成示例启动入口。
 */
public class VertxCqrsApplication {

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
            String orderId = COMMAND_BUS.execute(command);

            ctx.response().setStatusCode(201)
                    .putHeader("Content-Type", "application/json")
                    .end("{\"success\":true,\"orderId\":\"" + orderId + "\"}");
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

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        Router router = createRouter(vertx);
        VIEW_MANAGER.start();
        vertx.createHttpServer().requestHandler(router).listen(8085)
                .onSuccess(server -> System.out.println("Vert.x CQRS server started on port " + server.actualPort()));
    }
}
