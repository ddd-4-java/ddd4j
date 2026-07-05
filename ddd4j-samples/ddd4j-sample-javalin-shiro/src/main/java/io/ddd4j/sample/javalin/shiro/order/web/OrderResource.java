package io.ddd4j.sample.javalin.shiro.order.web;

import io.ddd4j.core.api.R;
import io.ddd4j.sample.javalin.shiro.order.application.AddOrderLineCommand;
import io.ddd4j.sample.javalin.shiro.order.application.CreateOrderCommand;
import io.ddd4j.sample.javalin.shiro.order.application.OrderApplicationService;
import io.ddd4j.sample.javalin.shiro.order.domain.model.Money;
import io.ddd4j.sample.javalin.shiro.order.domain.model.Order;
import io.ddd4j.sample.javalin.shiro.order.web.dto.AddOrderLineRequest;
import io.ddd4j.sample.javalin.shiro.order.web.dto.CreateOrderRequest;
import io.ddd4j.sample.javalin.shiro.order.web.dto.OrderResponse;
import io.javalin.apibuilder.EndpointGroup;

import java.util.List;
import java.util.Objects;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.post;

/**
 * 订单 REST 资源（Javalin 适配）。
 *
 * <p>通过 {@link EndpointGroup} 暴露路由注册入口，调用方在 Javalin 启动时通过
 * {@code javalinConfig.routes.apiBuilder(() -> orderResource.routes())} 注册。
 *
 * <h3>REST 端点</h3>
 * <ul>
 *   <li>POST /orders                    创建订单</li>
 *   <li>GET  /orders                    列表查询</li>
 *   <li>GET  /orders/{id}              查询订单</li>
 *   <li>GET  /orders/by-order-no       按订单号查询</li>
 *   <li>POST /orders/{id}/lines        添加订单行</li>
 *   <li>POST /orders/{id}/pay          支付订单</li>
 *   <li>POST /orders/{id}/ship         发货订单</li>
 *   <li>POST /orders/{id}/cancel       取消订单</li>
 *   <li>GET  /orders/{id}/discount     预览折扣</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class OrderResource {

    private final OrderApplicationService orderApplicationService;

    public OrderResource(OrderApplicationService orderApplicationService) {
        this.orderApplicationService = Objects.requireNonNull(orderApplicationService, "orderApplicationService must not be null");
    }

    /**
     * 以 {@link EndpointGroup} 形式暴露本资源的全部路由。
     */
    public EndpointGroup routes() {
        return () -> {
            // POST /orders —— 创建订单
            post("/orders", ctx -> {
                CreateOrderRequest req = ctx.bodyAsClass(CreateOrderRequest.class);
                Order order = orderApplicationService.createDraft(
                        new CreateOrderCommand(req.orderNo(), req.buyerId(), req.buyerName())
                );
                ctx.status(201).json(R.ok("order created", OrderResponse.from(order)));
            });

            // POST /orders/{id}/lines —— 添加订单行
            post("/orders/{id}/lines", ctx -> {
                String id = ctx.pathParam("id");
                AddOrderLineRequest req = ctx.bodyAsClass(AddOrderLineRequest.class);
                Order order = orderApplicationService.addLine(new AddOrderLineCommand(
                        id, req.goodsId(), req.goodsName(), req.quantity(), req.unitPrice()));
                ctx.json(R.ok("order line added", OrderResponse.from(order)));
            });

            // POST /orders/{id}/pay
            post("/orders/{id}/pay", ctx -> {
                String id = ctx.pathParam("id");
                Order order = orderApplicationService.pay(id);
                ctx.json(R.ok("order paid", OrderResponse.from(order)));
            });

            // POST /orders/{id}/ship
            post("/orders/{id}/ship", ctx -> {
                String id = ctx.pathParam("id");
                Order order = orderApplicationService.ship(id);
                ctx.json(R.ok("order shipped", OrderResponse.from(order)));
            });

            // POST /orders/{id}/cancel
            post("/orders/{id}/cancel", ctx -> {
                String id = ctx.pathParam("id");
                Order order = orderApplicationService.cancel(id);
                ctx.json(R.ok("order cancelled", OrderResponse.from(order)));
            });

            // GET /orders/{id}
            get("/orders/{id}", ctx -> {
                String id = ctx.pathParam("id");
                Order order = orderApplicationService.findById(id);
                ctx.json(R.ok(OrderResponse.from(order)));
            });

            // GET /orders/by-order-no?orderNo=xxx
            get("/orders/by-order-no", ctx -> {
                String orderNo = ctx.queryParam("orderNo");
                Order order = orderApplicationService.findByOrderNo(orderNo);
                ctx.json(R.ok(OrderResponse.from(order)));
            });

            // GET /orders —— 列表
            get("/orders", ctx -> {
                List<OrderResponse> items = orderApplicationService.listAll().stream()
                        .map(OrderResponse::from)
                        .toList();
                ctx.json(R.ok(items));
            });

            // GET /orders/{id}/discount
            get("/orders/{id}/discount", ctx -> {
                String id = ctx.pathParam("id");
                Money discounted = orderApplicationService.previewDiscount(id);
                ctx.json(R.ok(new DiscountView(discounted.amount().toPlainString(), discounted.currency())));
            });
        };
    }

    /** 折扣预览响应。 */
    public record DiscountView(String amount, String currency) {
    }
}