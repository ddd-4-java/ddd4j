package io.ddd4j.sample.richmodel.order.application;

import io.ddd4j.sample.richmodel.order.domain.model.Money;
import io.ddd4j.sample.richmodel.order.domain.model.Order;
import io.ddd4j.sample.richmodel.order.domain.repository.OrderRepository;

import java.util.Objects;

/**
 * 应用服务：编排业务用例，领域规则下沉到 Order 聚合中。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class OrderApplicationService {

    private final OrderRepository repository;

    /**
     * 构造函数。
     *
     * @param repository 订单仓库（不可为 null）
     */
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
        Order order = Order.draft(command.orderNo(), command.buyerId(), command.buyerName());
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
        order.addLine(command.productId(), command.productName(), command.quantity(), new Money(command.unitPrice(), "CNY"));
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
}
