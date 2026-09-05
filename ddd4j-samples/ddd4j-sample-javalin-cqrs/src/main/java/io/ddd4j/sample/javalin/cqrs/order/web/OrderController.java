package io.ddd4j.sample.javalin.cqrs.order.web;

import io.ddd4j.core.api.R;
import io.ddd4j.sample.javalin.cqrs.order.application.OrderApplicationService;
import io.ddd4j.sample.javalin.cqrs.order.application.OrderApplicationService.AddOrderLineCommand;
import io.ddd4j.sample.javalin.cqrs.order.application.OrderApplicationService.CreateOrderCommand;
import io.ddd4j.sample.javalin.cqrs.order.domain.model.Money;
import io.ddd4j.sample.javalin.cqrs.order.domain.model.Order;
import io.javalin.apibuilder.EndpointGroup;

import java.math.BigDecimal;
import java.util.Objects;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.post;

/**
 * 订单 REST 控制器（第二轨：充血模型）。
 *
 * <p>通过 {@link EndpointGroup} 暴露路由注册入口，调用方在 Javalin 启动时通过
 * {@code javalinConfig.routes.apiBuilder(() -> orderController.routes())} 注册。
 * 控制器只做"HTTP 协议层 → 应用服务"的翻译，业务规则全部下沉到 {@link Order} 聚合内。
 *
 * <h3>路由列表</h3>
 * <table border="1">
 *   <tr><th>HTTP</th><th>路径</th><th>用途</th></tr>
 *   <tr><td>POST</td><td>/api/orders</td><td>创建草稿订单</td></tr>
 *   <tr><td>POST</td><td>/api/orders/{id}/lines</td><td>添加订单行</td></tr>
 *   <tr><td>POST</td><td>/api/orders/{id}/pay</td><td>支付订单</td></tr>
 *   <tr><td>POST</td><td>/api/orders/{id}/ship</td><td>发货订单</td></tr>
 *   <tr><td>POST</td><td>/api/orders/{id}/cancel</td><td>取消订单</td></tr>
 *   <tr><td>GET</td><td>/api/orders/{id}</td><td>按 ID 查询</td></tr>
 *   <tr><td>GET</td><td>/api/orders/by-no</td><td>按订单编号查询</td></tr>
 * </table>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class OrderController {

    private final OrderApplicationService applicationService;

    public OrderController(OrderApplicationService applicationService) {
        this.applicationService = Objects.requireNonNull(applicationService, "applicationService must not be null");
    }

    private static OrderResponse toResponse(Order order) {
        Money total = order.totalAmount();
        return new OrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getBuyerId(),
                order.getBuyerName(),
                order.getStatus().getName(),
                total,
                order.lines().size());
    }

    // ========================= 响应 / 请求 DTO =========================

    /**
     * 以 {@link EndpointGroup} 形式暴露本控制器的全部路由。
     *
     * <pre>{@code
     * javalinConfig.routes.apiBuilder(() -> {
     *     orderController.routes();
     *     productController.routes();
     * });
     * }</pre>
     */
    public void routes() {
        // POST /api/orders —— 创建草稿订单
        post("/api/orders", ctx -> {
            CreateOrderRequest req = ctx.bodyAsClass(CreateOrderRequest.class);
            Order order = applicationService.createDraft(
                    new CreateOrderCommand(req.getOrderNo(), req.getBuyerId(), req.getBuyerName()));
            ctx.status(201).json(R.ok(toResponse(order)));
        });

        // POST /api/orders/{id}/lines —— 添加订单行
        post("/api/orders/{id}/lines", ctx -> {
            String orderId = ctx.pathParam("id");
            AddOrderLineRequest req = ctx.bodyAsClass(AddOrderLineRequest.class);
            Order order = applicationService.addLine(new AddOrderLineCommand(
                    orderId, req.goodsId(), req.goodsName(), req.quantity(), req.unitPrice()));
            ctx.json(R.ok(toResponse(order)));
        });

        // POST /api/orders/{id}/pay —— 支付
        post("/api/orders/{id}/pay", ctx -> {
            String orderId = ctx.pathParam("id");
            Order order = applicationService.pay(orderId);
            ctx.json(R.ok(toResponse(order)));
        });

        // POST /api/orders/{id}/ship —— 发货
        post("/api/orders/{id}/ship", ctx -> {
            String orderId = ctx.pathParam("id");
            Order order = applicationService.ship(orderId);
            ctx.json(R.ok(toResponse(order)));
        });

        // POST /api/orders/{id}/cancel —— 取消
        post("/api/orders/{id}/cancel", ctx -> {
            String orderId = ctx.pathParam("id");
            Order order = applicationService.cancel(orderId);
            ctx.json(R.ok(toResponse(order)));
        });

        // GET /api/orders/{id} —— 按 ID 查询
        get("/api/orders/{id}", ctx -> {
            String orderId = ctx.pathParam("id");
            Order order = applicationService.getById(orderId);
            ctx.json(R.ok(toResponse(order)));
        });

        // GET /api/orders/by-no?orderNo=xxx —— 按订单编号查询
        get("/api/orders/by-no", ctx -> {
            String orderNo = ctx.queryParam("orderNo");
            Order order = applicationService.getByOrderNo(orderNo);
            ctx.json(R.ok(toResponse(order)));
        });
    }

    /**
     * 订单响应（避免将充血领域模型直接序列化，保留聚合内字段语义）。
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

    /**
     * 创建订单请求。
     */public final class CreateOrderRequest {
        private final String orderNo;
        private final String buyerId;
        private final String buyerName;

        public CreateOrderRequest(String orderNo, String buyerId, String buyerName) {
            this.orderNo = orderNo;
            this.buyerId = buyerId;
            this.buyerName = buyerName;
        }
        public String orderNo() { return orderNo; }
        public String buyerId() { return buyerId; }
        public String buyerName() { return buyerName; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        CreateOrderRequest other = (CreateOrderRequest) o;
            return Objects.equals(this.orderNo, other.orderNo) && Objects.equals(this.buyerId, other.buyerId) && Objects.equals(this.buyerName, other.buyerName);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(orderNo, buyerId, buyerName); }
        @Override
        public String toString() {
            return "CreateOrderRequest{" + "orderNo=" + orderNo + ", " + "buyerId=" + buyerId + ", " + "buyerName=" + buyerName + "}";
        }
    
    }

    // ========================= 映射 =========================

    /**
     * 添加订单行请求。
     */public final class AddOrderLineRequest {
        private final String goodsId;
        private final String goodsName;
        private final int quantity;
        private final BigDecimal unitPrice;

        public AddOrderLineRequest(String goodsId, String goodsName, int quantity, BigDecimal unitPrice) {
            this.goodsId = goodsId;
            this.goodsName = goodsName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }
        public String goodsId() { return goodsId; }
        public String goodsName() { return goodsName; }
        public int quantity() { return quantity; }
        public BigDecimal unitPrice() { return unitPrice; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        AddOrderLineRequest other = (AddOrderLineRequest) o;
            return Objects.equals(this.goodsId, other.goodsId) && Objects.equals(this.goodsName, other.goodsName) && Objects.equals(this.quantity, other.quantity) && Objects.equals(this.unitPrice, other.unitPrice);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(goodsId, goodsName, quantity, unitPrice); }
        @Override
        public String toString() {
            return "AddOrderLineRequest{" + "goodsId=" + goodsId + ", " + "goodsName=" + goodsName + ", " + "quantity=" + quantity + ", " + "unitPrice=" + unitPrice + "}";
        }
    
    }
}
