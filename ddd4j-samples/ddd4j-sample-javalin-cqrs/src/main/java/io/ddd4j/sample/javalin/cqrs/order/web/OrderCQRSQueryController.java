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
                order.getId(),
                order.getOrderNo(),
                order.getBuyerId(),
                order.getBuyerName(),
                order.getStatus().getName(),
                order.totalAmount(),
                order.lines().size()
        ));
    }

    /**
     * 订单响应 record。
     */public final class OrderResponse {
        private final String id;
        private final String orderNo;
        private final String buyerId;
        private final String buyerName;
        private final String status;
        private final Money totalAmount;
        private final int lineCount;

        public OrderResponse(String id, String orderNo, String buyerId, String buyerName, String status, Money totalAmount, int lineCount) {
            this.id = id;
            this.orderNo = orderNo;
            this.buyerId = buyerId;
            this.buyerName = buyerName;
            this.status = status;
            this.totalAmount = totalAmount;
            this.lineCount = lineCount;
        }
        public String id() { return id; }
        public String orderNo() { return orderNo; }
        public String buyerId() { return buyerId; }
        public String buyerName() { return buyerName; }
        public String status() { return status; }
        public Money totalAmount() { return totalAmount; }
        public int lineCount() { return lineCount; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        OrderResponse other = (OrderResponse) o;
            return Objects.equals(this.id, other.id) && Objects.equals(this.orderNo, other.orderNo) && Objects.equals(this.buyerId, other.buyerId) && Objects.equals(this.buyerName, other.buyerName) && Objects.equals(this.status, other.status) && Objects.equals(this.totalAmount, other.totalAmount) && Objects.equals(this.lineCount, other.lineCount);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(id, orderNo, buyerId, buyerName, status, totalAmount, lineCount); }
        @Override
        public String toString() {
            return "OrderResponse{" + "id=" + id + ", " + "orderNo=" + orderNo + ", " + "buyerId=" + buyerId + ", " + "buyerName=" + buyerName + ", " + "status=" + status + ", " + "totalAmount=" + totalAmount + ", " + "lineCount=" + lineCount + "}";
        }
    
    }
}