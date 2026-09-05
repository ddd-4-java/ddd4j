package io.ddd4j.sample.javalin.cqrs.order.web;

import io.ddd4j.core.api.R;
import io.ddd4j.sample.javalin.cqrs.cache.OrderCacheService;
import io.ddd4j.sample.javalin.cqrs.order.domain.model.Money;
import io.ddd4j.sample.javalin.cqrs.order.domain.model.Order;

import java.util.Map;
import java.util.Objects;

import static io.javalin.apibuilder.ApiBuilder.get;

/**
 * 订单 CQRS 读侧控制器 - 缓存增强版本（Javalin）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class OrderCQRSQueryController {

    private final OrderCacheService orderCacheService;

    public OrderCQRSQueryController(OrderCacheService orderCacheService) {
        this.orderCacheService = Objects.requireNonNull(orderCacheService, "orderCacheService must not be null");
    }

    /**
     * 路由注册入口。
     */
    public void routes() {
        // GET /api/orders/query/list
        get("/api/orders/query/list", ctx -> {
            int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(1);
            int pageSize = ctx.queryParamAsClass("pageSize", Integer.class).getOrDefault(10);
            ctx.json(R.ok(Map.of("page", page, "pageSize", pageSize, "note", "see /api/orders/query/stats")));
        });

        // GET /api/orders/query/stats
        get("/api/orders/query/stats", ctx -> ctx.json(R.ok(orderCacheService.getOrderStats())));

        // GET /api/orders/query/buyer/{buyerId}/count
        get("/api/orders/query/buyer/{buyerId}/count", ctx -> {
            String buyerId = ctx.pathParam("buyerId");
            long count = orderCacheService.getBuyerOrderCount(buyerId);
            ctx.json(R.ok(Map.of("buyerId", buyerId, "count", count)));
        });

        // GET /api/orders/query/detail/{id}
        get("/api/orders/query/detail/{id}", ctx -> {
            String id = ctx.pathParam("id");
            R<OrderResponse> body = orderCacheService.getOrderDetail(id)
                    .map(this::toResponse)
                    .orElseGet(() -> R.fail("404", "order not found: " + id));
            ctx.json(body);
        });
    }

    private R<OrderResponse> toResponse(Order order) {
        return R.ok(new OrderResponse(
                order.id(),
                order.orderNo(),
                order.buyerId(),
                order.buyerName(),
                order.status().name(),
                order.totalAmount(),
                order.lines().size()
        ));
    }

    /**
     * 订单响应 record。
     */
    public record OrderResponse(
            String id,
            String orderNo,
            String buyerId,
            String buyerName,
            String status,
            Money totalAmount,
            int lineCount) {
    }
}