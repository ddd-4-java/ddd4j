package io.ddd4j.sample.spring.cqrs.order.domain.model;

import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.spring.cqrs.order.domain.event.OrderCancelledEvent;
import io.ddd4j.sample.spring.cqrs.order.domain.event.OrderCreatedEvent;
import io.ddd4j.sample.spring.cqrs.order.domain.event.OrderLineAddedEvent;
import io.ddd4j.sample.spring.cqrs.order.domain.event.OrderPaidEvent;
import io.ddd4j.sample.spring.cqrs.order.domain.event.OrderShippedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 订单聚合根（充血模型）。
 *
 * <p>继承 ddd4j {@link AggregateRoot}，封装完整的业务行为与状态机不变式：
 * <ul>
 *   <li>{@link #draft} — 创建草稿订单，触发 {@link OrderCreatedEvent}</li>
 *   <li>{@link #addLine} — 添加订单行，仅 DRAFT 状态可操作</li>
 *   <li>{@link #pay} — 支付订单，DRAFT → PAID</li>
 *   <li>{@link #ship} — 发货订单，PAID → SHIPPED</li>
 *   <li>{@link #cancel} — 取消订单，SHIPPED 不可取消</li>
 * </ul>
 *
 * <p>所有业务方法在修改状态后通过 {@link #registerEvent} 注册领域事件，
 * 由应用服务在事务提交后统一发布。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class Order extends AggregateRoot<String> {

    private static final long serialVersionUID = 1L;

    /** 订单 ID */
    private final String id;
    /** 订单编号 */
    private final String orderNo;
    /** 买家 ID */
    private final String buyerId;
    /** 买家名称 */
    private String buyerName;
    /** 订单状态 */
    private OrderStatus status;
    /** 订单行列表 */
    private final List<OrderLine> lines = new ArrayList<>();

    /**
     * 构造函数。
     *
     * @param id        订单 ID
     * @param orderNo   订单编号
     * @param buyerId   买家 ID
     * @param buyerName 买家名称
     * @param status    订单状态
     * @param lines     订单行列表
     */
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
     * <p>创建后自动注册 {@link OrderCreatedEvent}。
     *
     * @param orderNo   订单编号
     * @param buyerId   买家 ID
     * @param buyerName 买家名称
     * @return 草稿订单
     */
    public static Order draft(String orderNo, String buyerId, String buyerName) {
        Order order = new Order(UUID.randomUUID().toString(), orderNo, buyerId, buyerName, OrderStatus.DRAFT, List.of());
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
     * 获取订单行列表（不可变视图）。
     *
     * @return 订单行列表
     */
    public List<OrderLine> lines() {
        return List.copyOf(lines);
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
     * <p>仅 DRAFT 状态可操作，添加后注册 {@link OrderLineAddedEvent}。
     *
     * @param goodsId   商品 ID
     * @param goodsName 商品名称
     * @param quantity    数量
     * @param unitPrice   单价
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
     * <p>仅 DRAFT 状态且订单行非空时可支付，支付后注册 {@link OrderPaidEvent}。
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
     * <p>仅 PAID 状态可发货，发货后注册 {@link OrderShippedEvent}。
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
     * <p>已发货订单不可取消，取消后注册 {@link OrderCancelledEvent}。
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

    /**
     * 断言当前订单为草稿状态。
     */
    private void assertDraft() {
        if (status != OrderStatus.DRAFT) {
            throw new IllegalStateException("only draft order can be changed");
        }
    }
}
