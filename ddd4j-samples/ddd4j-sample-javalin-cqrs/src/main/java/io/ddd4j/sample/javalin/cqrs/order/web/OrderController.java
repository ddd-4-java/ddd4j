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
 *   <caption>订单路由</caption>
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

    /**
     * 创建订单控制器。
     *
     * @param applicationService 应用服务
     */
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
    public final static class OrderResponse {

        private static final long serialVersionUID = 0L;

        private final String id;

        private final String orderNo;

        private final String buyerId;

        private final String buyerName;

        private final String status;

        private final Money totalAmount;

        private final int lineCount;

        /** @return id */
        public String getId() {
            return id;
        }

        /** @return orderNo */
        public String getOrderNo() {
            return orderNo;
        }

        /** @return buyerId */
        public String getBuyerId() {
            return buyerId;
        }

        /** @return buyerName */
        public String getBuyerName() {
            return buyerName;
        }

        /** @return status */
        public String getStatus() {
            return status;
        }

        /** @return totalAmount */
        public Money getTotalAmount() {
            return totalAmount;
        }

        /** @return lineCount */
        public int getLineCount() {
            return lineCount;
        }

        /**
         * 创建订单响应数据。
         *
         * @param id 标识
         * @param orderNo 订单编号
         * @param buyerId 买家标识
         * @param buyerName 买家名称
         * @param status 状态
         * @param totalAmount 订单总金额
         * @param lineCount 订单行数
         */
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

        /**
         * 读取标识。
         *
         * @return 标识
         */
        @JsonProperty("id")
        public String id() {
            return id;
        }

        /**
         * 读取订单编号。
         *
         * @return 订单编号
         */
        @JsonProperty("orderNo")
        public String orderNo() {
            return orderNo;
        }

        /**
         * 读取买家标识。
         *
         * @return 买家标识
         */
        @JsonProperty("buyerId")
        public String buyerId() {
            return buyerId;
        }

        /**
         * 读取买家名称。
         *
         * @return 买家名称
         */
        @JsonProperty("buyerName")
        public String buyerName() {
            return buyerName;
        }

        /**
         * 读取状态。
         *
         * @return 订单状态
         */
        @JsonProperty("status")
        public String status() {
            return status;
        }

        /**
         * 读取订单总金额。
         *
         * @return 订单总金额
         */
        @JsonProperty("totalAmount")
        public Money totalAmount() {
            return totalAmount;
        }

        /**
         * 读取订单行数。
         *
         * @return 订单行数
         */
        @JsonProperty("lineCount")
        public int lineCount() {
            return lineCount;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            OrderResponse other = (OrderResponse) obj;
            return Objects.equals(this.id, other.id) && Objects.equals(this.orderNo, other.orderNo) && Objects.equals(this.buyerId, other.buyerId) && Objects.equals(this.buyerName, other.buyerName) && Objects.equals(this.status, other.status) && Objects.equals(this.totalAmount, other.totalAmount) && this.lineCount == other.lineCount;
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(id);
            result = 31 * result + Objects.hashCode(orderNo);
            result = 31 * result + Objects.hashCode(buyerId);
            result = 31 * result + Objects.hashCode(buyerName);
            result = 31 * result + Objects.hashCode(status);
            result = 31 * result + Objects.hashCode(totalAmount);
            result = 31 * result + Integer.hashCode(lineCount);
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
    public final static class CreateOrderRequest {

        private static final long serialVersionUID = 0L;

        private String orderNo;

        private String buyerId;

        private String buyerName;

        /** 创建供请求绑定使用的空对象。 */
        public CreateOrderRequest() {
        }

        /** @return 订单编号 */
        public String getOrderNo() {
            return orderNo;
        }

        /** @param orderNo 订单编号 */
        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }

        /** @return 买家标识 */
        public String getBuyerId() {
            return buyerId;
        }

        /** @param buyerId 买家标识 */
        public void setBuyerId(String buyerId) {
            this.buyerId = buyerId;
        }

        /** @return 买家名称 */
        public String getBuyerName() {
            return buyerName;
        }

        /** @param buyerName 买家名称 */
        public void setBuyerName(String buyerName) {
            this.buyerName = buyerName;
        }

        /**
         * 创建订单请求数据。
         *
         * @param orderNo 订单编号
         * @param buyerId 买家标识
         * @param buyerName 买家名称
         */
        @JsonCreator()
        public CreateOrderRequest(@JsonProperty("orderNo") String orderNo, @JsonProperty("buyerId") String buyerId, @JsonProperty("buyerName") String buyerName) {
            this.orderNo = orderNo;
            this.buyerId = buyerId;
            this.buyerName = buyerName;
        }

        /**
         * 读取订单编号。
         *
         * @return 订单编号
         */
        @JsonProperty("orderNo")
        public String orderNo() {
            return orderNo;
        }

        /**
         * 读取买家标识。
         *
         * @return 买家标识
         */
        @JsonProperty("buyerId")
        public String buyerId() {
            return buyerId;
        }

        /**
         * 读取买家名称。
         *
         * @return 买家名称
         */
        @JsonProperty("buyerName")
        public String buyerName() {
            return buyerName;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            CreateOrderRequest other = (CreateOrderRequest) obj;
            return Objects.equals(this.orderNo, other.orderNo) && Objects.equals(this.buyerId, other.buyerId) && Objects.equals(this.buyerName, other.buyerName);
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(orderNo);
            result = 31 * result + Objects.hashCode(buyerId);
            result = 31 * result + Objects.hashCode(buyerName);
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
    public final static class AddOrderLineRequest {

        private static final long serialVersionUID = 0L;

        private String goodsId;

        private String goodsName;

        private int quantity;

        private BigDecimal unitPrice;

        /** 创建供请求绑定使用的空对象。 */
        public AddOrderLineRequest() {
        }

        /** @return 商品标识 */
        public String getGoodsId() {
            return goodsId;
        }

        /** @param goodsId 商品标识 */
        public void setGoodsId(String goodsId) {
            this.goodsId = goodsId;
        }

        /** @return 商品名称 */
        public String getGoodsName() {
            return goodsName;
        }

        /** @param goodsName 商品名称 */
        public void setGoodsName(String goodsName) {
            this.goodsName = goodsName;
        }

        /** @return 数量 */
        public int getQuantity() {
            return quantity;
        }

        /** @param quantity 数量 */
        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        /** @return 单价 */
        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        /** @param unitPrice 单价 */
        public void setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }

        /**
         * 创建添加订单行请求数据。
         *
         * @param goodsId 商品标识
         * @param goodsName 商品名称
         * @param quantity 购买数量
         * @param unitPrice 商品单价
         */
        @JsonCreator()
        public AddOrderLineRequest(@JsonProperty("goodsId") String goodsId, @JsonProperty("goodsName") String goodsName, @JsonProperty("quantity") int quantity, @JsonProperty("unitPrice") BigDecimal unitPrice) {
            this.goodsId = goodsId;
            this.goodsName = goodsName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        /**
         * 读取商品标识。
         *
         * @return 商品标识
         */
        @JsonProperty("goodsId")
        public String goodsId() {
            return goodsId;
        }

        /**
         * 读取商品名称。
         *
         * @return 商品名称
         */
        @JsonProperty("goodsName")
        public String goodsName() {
            return goodsName;
        }

        /**
         * 读取购买数量。
         *
         * @return 购买数量
         */
        @JsonProperty("quantity")
        public int quantity() {
            return quantity;
        }

        /**
         * 读取商品单价。
         *
         * @return 商品单价
         */
        @JsonProperty("unitPrice")
        public BigDecimal unitPrice() {
            return unitPrice;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            AddOrderLineRequest other = (AddOrderLineRequest) obj;
            return Objects.equals(this.goodsId, other.goodsId) && Objects.equals(this.goodsName, other.goodsName) && this.quantity == other.quantity && Objects.equals(this.unitPrice, other.unitPrice);
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(goodsId);
            result = 31 * result + Objects.hashCode(goodsName);
            result = 31 * result + Integer.hashCode(quantity);
            result = 31 * result + Objects.hashCode(unitPrice);
            return result;
        }

        @Override
        public String toString() {
            return "AddOrderLineRequest[goodsId=" + goodsId + ", goodsName=" + goodsName + ", quantity=" + quantity + ", unitPrice=" + unitPrice + "]";
        }
    }
}
