package io.ddd4j.sample.quarkus.cqrs.order.domain.model;

import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.quarkus.cqrs.order.domain.event.OrderCancelledEvent;
import io.ddd4j.sample.quarkus.cqrs.order.domain.event.OrderCreatedEvent;
import io.ddd4j.sample.quarkus.cqrs.order.domain.event.OrderPaidEvent;
import io.ddd4j.sample.quarkus.cqrs.order.domain.event.OrderShippedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 订单聚合根（充血模型示例）。
 *
 * <p>本模型刻意不依赖 JPA / Hibernate / Quarkus / MyBatis 注解，
 * 仅承载领域规则。持久化适配层位于 {@code infrastructure} 子包。
 *
 * <p>业务行为：{@link #draft} 创建草稿、{@link #addLine} 添加商品、
 * {@link #pay} 支付、{@link #ship} 发货、{@link #cancel} 取消。
 * 每次状态变更均会注册 {@link OrderCreatedEvent}/{@link OrderPaidEvent}
 * 等领域事件，由 {@code DomainEventPublisher} SPI 转发到进程内监听者。
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
     * 订单行列表（聚合内的实体集合）
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
     * 创建草稿订单（聚合根工厂方法）。
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
     * 获取订单行列表（不可变）。
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
     * @param goodsId   商品 ID
     * @param goodsName 商品名称
     * @param quantity  数量
     * @param unitPrice 单价
     */
    public void addLine(String goodsId, String goodsName, int quantity, Money unitPrice) {
        assertDraft();
        OrderLine line = new OrderLine(goodsId, goodsName, quantity, unitPrice);
        lines.add(line);
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