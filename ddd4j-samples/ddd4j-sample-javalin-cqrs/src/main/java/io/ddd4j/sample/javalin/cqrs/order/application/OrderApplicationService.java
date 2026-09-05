package io.ddd4j.sample.javalin.cqrs.order.application;

import io.ddd4j.sample.javalin.cqrs.order.domain.model.Money;
import io.ddd4j.sample.javalin.cqrs.order.domain.model.Order;
import io.ddd4j.sample.javalin.cqrs.order.domain.repository.OrderRepository;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 应用服务：编排业务用例，领域规则下沉到 {@link Order} 聚合中。
 *
 * <p>本服务只承担"用例编排 + 事务边界 + 事件发布"职责，
 * 业务校验（状态机、不变量）全部下沉到聚合根的充血方法中。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class OrderApplicationService {

    private final OrderRepository repository;

    public OrderApplicationService(OrderRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /**
     * 创建草稿订单。
     *
     * @param command 创建订单命令
     * @return 创建的订单聚合
     */
    public Order createDraft(CreateOrderCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Order order = Order.draft(command.getOrderNo(), command.getBuyerId(), command.getBuyerName());
        repository.save(order);
        return order;
    }

    /**
     * 添加订单行。
     *
     * @param command 添加订单行命令
     * @return 更新后的订单聚合
     */
    public Order addLine(AddOrderLineCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Order order = repository.findById(command.orderId())
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + command.orderId()));
        Money unitPrice = Objects.isNull(command.unitPrice())
                ? Money.zero("CNY")
                : Money.cny(command.unitPrice());
        order.addLine(command.goodsId(), command.goodsName(), command.quantity(), unitPrice);
        repository.save(order);
        return order;
    }

    /**
     * 支付订单。
     *
     * @param orderId 订单 ID
     * @return 支付后的订单聚合
     */
    public Order pay(String orderId) {
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
        order.pay();
        repository.save(order);
        return order;
    }

    /**
     * 发货订单。
     *
     * @param orderId 订单 ID
     * @return 发货后的订单聚合
     */
    public Order ship(String orderId) {
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
        order.ship();
        repository.save(order);
        return order;
    }

    /**
     * 取消订单。
     *
     * @param orderId 订单 ID
     * @return 取消后的订单聚合
     */
    public Order cancel(String orderId) {
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
        order.cancel();
        repository.save(order);
        return order;
    }

    /**
     * 按 ID 查询订单。
     *
     * @param orderId 订单 ID
     * @return 订单聚合
     */
    public Order getById(String orderId) {
        return repository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
    }

    /**
     * 按订单编号查询订单。
     *
     * @param orderNo 订单编号
     * @return 订单聚合
     */
    public Order getByOrderNo(String orderNo) {
        return repository.findByOrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("order not found by orderNo: " + orderNo));
    }

    /**
     * 创建订单命令。
     *
     * @param orderNo   订单编号
     * @param buyerId   买家 ID
     * @param buyerName 买家显示名称
     */public final class CreateOrderCommand {
        private final String orderNo;
        private final String buyerId;
        private final String buyerName;

        public CreateOrderCommand(String orderNo, String buyerId, String buyerName) {
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
        CreateOrderCommand other = (CreateOrderCommand) o;
            return Objects.equals(this.orderNo, other.orderNo) && Objects.equals(this.buyerId, other.buyerId) && Objects.equals(this.buyerName, other.buyerName);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(orderNo, buyerId, buyerName); }
        @Override
        public String toString() {
            return "CreateOrderCommand{" + "orderNo=" + orderNo + ", " + "buyerId=" + buyerId + ", " + "buyerName=" + buyerName + "}";
        }
    
    }

    /**
     * 添加订单行命令。
     *
     * @param orderId   订单 ID
     * @param goodsId   商品 ID
     * @param goodsName 商品名称
     * @param quantity  购买数量
     * @param unitPrice 单价
     */public final class AddOrderLineCommand {
        private final String orderId;
        private final String goodsId;
        private final String goodsName;
        private final int quantity;
        private final BigDecimal unitPrice;

        public AddOrderLineCommand(String orderId, String goodsId, String goodsName, int quantity, BigDecimal unitPrice) {
            this.orderId = orderId;
            this.goodsId = goodsId;
            this.goodsName = goodsName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }
        public String orderId() { return orderId; }
        public String goodsId() { return goodsId; }
        public String goodsName() { return goodsName; }
        public int quantity() { return quantity; }
        public BigDecimal unitPrice() { return unitPrice; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        AddOrderLineCommand other = (AddOrderLineCommand) o;
            return Objects.equals(this.orderId, other.orderId) && Objects.equals(this.goodsId, other.goodsId) && Objects.equals(this.goodsName, other.goodsName) && Objects.equals(this.quantity, other.quantity) && Objects.equals(this.unitPrice, other.unitPrice);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(orderId, goodsId, goodsName, quantity, unitPrice); }
        @Override
        public String toString() {
            return "AddOrderLineCommand{" + "orderId=" + orderId + ", " + "goodsId=" + goodsId + ", " + "goodsName=" + goodsName + ", " + "quantity=" + quantity + ", " + "unitPrice=" + unitPrice + "}";
        }
    
    }
}
