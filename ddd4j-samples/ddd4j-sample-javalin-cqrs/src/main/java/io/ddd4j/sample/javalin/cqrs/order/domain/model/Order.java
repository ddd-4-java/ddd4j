package io.ddd4j.sample.javalin.cqrs.order.domain.model;

import java.util.Collections;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.javalin.cqrs.order.domain.event.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 订单聚合根（第二轨：充血模型）。
 *
 * <p>该模型刻意不依赖 MyBatis、JPA、Spring、Javalin 注解。
 * 状态机、不变量、领域事件全部下沉到聚合内：外部只能通过
 * {@link #addLine}/{@link #pay}/{@link #ship}/{@link #cancel} 等
 * 业务行为修改订单，所有状态迁移均触发领域事件。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class Order extends AggregateRoot<String> {

    private static final long serialVersionUID = 1L;

    /**
     * 订单 ID
     */
    private final String id;
    /**
     * 订单编号
     */
    private final String orderNo;
    /**
     * 买家 ID
     */
    private final String buyerId;
    /**
     * 订单行列表
     */
    private final List<OrderLine> lines = new ArrayList<>();
    /**
     * 买家名称
     */
    private String buyerName;
    /**
     * 订单状态
     */
    private OrderStatus status;

    public Order(String id, String orderNo, String buyerId, String buyerName, OrderStatus status, List<OrderLine> lines) {
        if (StrKit.isBlank(id)) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (StrKit.isBlank(orderNo)) {
            throw new IllegalArgumentException("orderNo must not be blank");
        }
        if (StrKit.isBlank(buyerId)) {
            throw new IllegalArgumentException("buyerId must not be blank");
        }
        this.id = id;
        this.orderNo = orderNo;
        this.buyerId = buyerId;
        renameBuyer(buyerName);
        this.status = Objects.requireNonNull(status, "status must not be null");
        if (Objects.nonNull(lines)) {
            this.lines.addAll(lines);
        }
    }

    /**
     * 创建草稿订单（工厂方法）。
     *
     * @param orderNo   订单编号
     * @param buyerId   买家 ID
     * @param buyerName 买家名称
     * @return 草稿订单
     */
    public static Order draft(String orderNo, String buyerId, String buyerName) {
        Order order = new Order(UUID.randomUUID().toString(), orderNo, buyerId, buyerName, OrderStatus.DRAFT, Collections.emptyList());
        order.registerEvent(new OrderCreatedEvent(order.id()));
        return order;
    }

    @Override
    public String id() {
        return id;
    }

    public String orderNo() {
        return orderNo;
    }

    public String buyerId() {
        return buyerId;
    }

    public String buyerName() {
        return buyerName;
    }

    public OrderStatus status() {
        return status;
    }

    /**
     * 获取订单行列表（不可变）。
     *
     * @return 订单行列表
     */
    public List<OrderLine> lines() {
        return Collections.unmodifiableList(new ArrayList<>(lines));
    }

    /**
     * 计算订单总金额。
     *
     * @return 总金额
     */
    public Money totalAmount() {
        return lines.stream()
                .map(OrderLine::subtotal)
                .reduce(Money.zero("CNY"), Money::add);
    }

    /**
     * 重命名买家。
     *
     * @param buyerName 新买家名称
     */
    public void renameBuyer(String buyerName) {
        if (StrKit.isBlank(buyerName)) {
            throw new IllegalArgumentException("buyerName must not be blank");
        }
        this.buyerName = buyerName;
    }

    /**
     * 添加订单行。
     *
     * @param goodsId   商品 ID
     * @param goodsName 商品名称
     * @param quantity  数量
     * @param unitPrice 单价
     */
    public void addLine(String goodsId, String goodsName, int quantity, Money unitPrice) {
        assertDraft();
        OrderLine line = OrderLine.create(goodsId, goodsName, quantity, unitPrice);
        lines.add(line);
        registerEvent(new OrderLineAddedEvent(id));
    }

    /**
     * 支付订单。
     *
     * @throws IllegalStateException 如果订单不是草稿状态或订单行为空
     */
    public void pay() {
        assertDraft();
        if (lines.isEmpty()) {
            throw new IllegalStateException("order line must not be empty");
        }
        status = OrderStatus.PAID;
        registerEvent(new OrderPaidEvent(id));
    }

    /**
     * 发货订单。
     *
     * @throws IllegalStateException 如果订单不是已支付状态
     */
    public void ship() {
        if (status != OrderStatus.PAID) {
            throw new IllegalStateException("only paid order can be shipped");
        }
        status = OrderStatus.SHIPPED;
        registerEvent(new OrderShippedEvent(id));
    }

    /**
     * 取消订单。
     *
     * @throws IllegalStateException 如果订单已发货
     */
    public void cancel() {
        if (status == OrderStatus.SHIPPED) {
            throw new IllegalStateException("shipped order cannot be cancelled");
        }
        status = OrderStatus.CANCELLED;
        registerEvent(new OrderCancelledEvent(id));
    }

    private void assertDraft() {
        if (status != OrderStatus.DRAFT) {
            throw new IllegalStateException("only draft order can be changed");
        }
    }
}
