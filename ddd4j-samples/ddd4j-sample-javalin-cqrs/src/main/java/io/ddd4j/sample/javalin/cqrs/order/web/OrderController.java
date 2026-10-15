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
package io.ddd4j.sample.javalin.cqrs.order.web;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

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
 * <h2>路由列表</h2>
 * <table border="1">
 * <caption>订单管理 REST 接口一览</caption>
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
                order.id(),
                order.orderNo(),
                order.buyerId(),
                order.buyerName(),
                order.status().name(),
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
                    new CreateOrderCommand(req.orderNo(), req.buyerId(), req.buyerName()));
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
     */
    public static class OrderResponse {

        private static final long serialVersionUID = 0L;

        private final String id;

        private final String orderNo;

        private final String buyerId;

        private final String buyerName;

        private final String status;

        private final Money totalAmount;

        private final int lineCount;

        public String getId() {
            return id;
        }

        public String getOrderNo() {
            return orderNo;
        }

        public String getBuyerId() {
            return buyerId;
        }

        public String getBuyerName() {
            return buyerName;
        }

        public String getStatus() {
            return status;
        }

        public Money getTotalAmount() {
            return totalAmount;
        }

        public int getLineCount() {
            return lineCount;
        }

        @JsonCreator()
        public OrderResponse(@JsonProperty("id") String id, @JsonProperty("orderNo") String orderNo, @JsonProperty("buyerId") String buyerId, @JsonProperty("buyerName") String buyerName, @JsonProperty("status") String status, @JsonProperty("totalAmount") Money totalAmount, @JsonProperty("lineCount") int lineCount) {
            this.id = id;
            this.orderNo = orderNo;
            this.buyerId = buyerId;
            this.buyerName = buyerName;
            this.status = status;
            this.totalAmount = totalAmount;
            this.lineCount = lineCount;
        }

        @JsonProperty("id")
        public String id() {
            return id;
        }

        @JsonProperty("orderNo")
        public String orderNo() {
            return orderNo;
        }

        @JsonProperty("buyerId")
        public String buyerId() {
            return buyerId;
        }

        @JsonProperty("buyerName")
        public String buyerName() {
            return buyerName;
        }

        @JsonProperty("status")
        public String status() {
            return status;
        }

        @JsonProperty("totalAmount")
        public Money totalAmount() {
            return totalAmount;
        }

        @JsonProperty("lineCount")
        public int lineCount() {
            return lineCount;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof OrderResponse)) {
                return false;
            }
            OrderResponse other = (OrderResponse) obj;
            if (!other.canEqual(this)) {
                return false;
            }
            return Objects.equals(this.id, other.id) && Objects.equals(this.orderNo, other.orderNo) && Objects.equals(this.buyerId, other.buyerId) && Objects.equals(this.buyerName, other.buyerName) && Objects.equals(this.status, other.status) && Objects.equals(this.totalAmount, other.totalAmount) && this.lineCount == other.lineCount;
        }

        /**
         * 保留 OrderResponse 可继承值对象的对称相等性扩展点。
         *
         * @param other 待比较对象
         * @return 子类是否允许与当前值对象比较
         */
        protected boolean canEqual(Object other) {
            return other instanceof OrderResponse;
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 59 * result + lineCount;
            result = 59 * result + (Objects.isNull(id) ? 43 : id.hashCode());
            result = 59 * result + (Objects.isNull(orderNo) ? 43 : orderNo.hashCode());
            result = 59 * result + (Objects.isNull(buyerId) ? 43 : buyerId.hashCode());
            result = 59 * result + (Objects.isNull(buyerName) ? 43 : buyerName.hashCode());
            result = 59 * result + (Objects.isNull(status) ? 43 : status.hashCode());
            result = 59 * result + (Objects.isNull(totalAmount) ? 43 : totalAmount.hashCode());
            return result;
        }

        @Override
        public String toString() {
            return "OrderResponse[id=" + id + ", orderNo=" + orderNo + ", buyerId=" + buyerId + ", buyerName=" + buyerName + ", status=" + status + ", totalAmount=" + totalAmount + ", lineCount=" + lineCount + "]";
        }
    }

    /**
     * 创建订单请求。
     */
    public static class CreateOrderRequest {

        private static final long serialVersionUID = 0L;

        private String orderNo;

        private String buyerId;

        private String buyerName;

        public CreateOrderRequest() {
        }

        public String getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }

        public String getBuyerId() {
            return buyerId;
        }

        public void setBuyerId(String buyerId) {
            this.buyerId = buyerId;
        }

        public String getBuyerName() {
            return buyerName;
        }

        public void setBuyerName(String buyerName) {
            this.buyerName = buyerName;
        }

        @JsonCreator()
        public CreateOrderRequest(@JsonProperty("orderNo") String orderNo, @JsonProperty("buyerId") String buyerId, @JsonProperty("buyerName") String buyerName) {
            this.orderNo = orderNo;
            this.buyerId = buyerId;
            this.buyerName = buyerName;
        }

        @JsonProperty("orderNo")
        public String orderNo() {
            return orderNo;
        }

        @JsonProperty("buyerId")
        public String buyerId() {
            return buyerId;
        }

        @JsonProperty("buyerName")
        public String buyerName() {
            return buyerName;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CreateOrderRequest)) {
                return false;
            }
            CreateOrderRequest other = (CreateOrderRequest) obj;
            if (!other.canEqual(this)) {
                return false;
            }
            return Objects.equals(this.orderNo, other.orderNo) && Objects.equals(this.buyerId, other.buyerId) && Objects.equals(this.buyerName, other.buyerName);
        }

        /**
         * 保留 CreateOrderRequest 可继承值对象的对称相等性扩展点。
         *
         * @param other 待比较对象
         * @return 子类是否允许与当前值对象比较
         */
        protected boolean canEqual(Object other) {
            return other instanceof CreateOrderRequest;
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 59 * result + (Objects.isNull(orderNo) ? 43 : orderNo.hashCode());
            result = 59 * result + (Objects.isNull(buyerId) ? 43 : buyerId.hashCode());
            result = 59 * result + (Objects.isNull(buyerName) ? 43 : buyerName.hashCode());
            return result;
        }

        @Override
        public String toString() {
            return "CreateOrderRequest[orderNo=" + orderNo + ", buyerId=" + buyerId + ", buyerName=" + buyerName + "]";
        }
    }

    // ========================= 映射 =========================

    /**
     * 添加订单行请求。
     */
    public static class AddOrderLineRequest {

        private static final long serialVersionUID = 0L;

        private String goodsId;

        private String goodsName;

        private int quantity;

        private BigDecimal unitPrice;

        public AddOrderLineRequest() {
        }

        public String getGoodsId() {
            return goodsId;
        }

        public void setGoodsId(String goodsId) {
            this.goodsId = goodsId;
        }

        public String getGoodsName() {
            return goodsName;
        }

        public void setGoodsName(String goodsName) {
            this.goodsName = goodsName;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }

        @JsonCreator()
        public AddOrderLineRequest(@JsonProperty("goodsId") String goodsId, @JsonProperty("goodsName") String goodsName, @JsonProperty("quantity") int quantity, @JsonProperty("unitPrice") BigDecimal unitPrice) {
            this.goodsId = goodsId;
            this.goodsName = goodsName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        @JsonProperty("goodsId")
        public String goodsId() {
            return goodsId;
        }

        @JsonProperty("goodsName")
        public String goodsName() {
            return goodsName;
        }

        @JsonProperty("quantity")
        public int quantity() {
            return quantity;
        }

        @JsonProperty("unitPrice")
        public BigDecimal unitPrice() {
            return unitPrice;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof AddOrderLineRequest)) {
                return false;
            }
            AddOrderLineRequest other = (AddOrderLineRequest) obj;
            if (!other.canEqual(this)) {
                return false;
            }
            return Objects.equals(this.goodsId, other.goodsId) && Objects.equals(this.goodsName, other.goodsName) && this.quantity == other.quantity && Objects.equals(this.unitPrice, other.unitPrice);
        }

        /**
         * 保留 AddOrderLineRequest 可继承值对象的对称相等性扩展点。
         *
         * @param other 待比较对象
         * @return 子类是否允许与当前值对象比较
         */
        protected boolean canEqual(Object other) {
            return other instanceof AddOrderLineRequest;
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 59 * result + quantity;
            result = 59 * result + (Objects.isNull(goodsId) ? 43 : goodsId.hashCode());
            result = 59 * result + (Objects.isNull(goodsName) ? 43 : goodsName.hashCode());
            result = 59 * result + (Objects.isNull(unitPrice) ? 43 : unitPrice.hashCode());
            return result;
        }

        @Override
        public String toString() {
            return "AddOrderLineRequest[goodsId=" + goodsId + ", goodsName=" + goodsName + ", quantity=" + quantity + ", unitPrice=" + unitPrice + "]";
        }
    }
}
