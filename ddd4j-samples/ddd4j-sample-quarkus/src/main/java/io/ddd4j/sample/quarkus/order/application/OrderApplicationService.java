package io.ddd4j.sample.quarkus.order.application;

import io.ddd4j.sample.quarkus.order.cache.OrderCacheService;
import io.ddd4j.sample.quarkus.order.domain.model.Money;
import io.ddd4j.sample.quarkus.order.domain.model.Order;
import io.ddd4j.sample.quarkus.order.domain.repository.OrderRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Objects;

/**
 * 应用服务：编排订单业务用例，领域规则下沉到 Order 聚合中。
 *
 * <p>本服务由 Quarkus CDI 管理（{@link ApplicationScoped}），
 * 通过构造器注入 {@link OrderRepository} 与 {@link OrderCacheService}。
 * 所有业务调用均先经过应用服务，再委托给聚合根的方法
 * （如 {@link Order#pay} / {@link Order#ship}）。
 *
 * <p>写操作完成后同步更新缓存（{@link OrderCacheService}），
 * 保证 CQRS 查询端读到最新数据。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ApplicationScoped
public class OrderApplicationService {

    private final OrderRepository repository;
    private final OrderCacheService cacheService;

    /**
     * CDI 构造器注入（推荐方式，便于单测与容器生命周期解耦）。
     *
     * @param repository   订单仓库
     * @param cacheService 订单缓存服务
     */
    @Inject
    public OrderApplicationService(OrderRepository repository, OrderCacheService cacheService) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.cacheService = Objects.requireNonNull(cacheService, "cacheService must not be null");
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
        cacheService.putOrder(order);
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
        order.addLine(command.goodsId(), command.goodsName(), command.quantity(),
                new Money(command.unitPrice(), "CNY"));
        repository.save(order);
        cacheService.putOrder(order);
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
        cacheService.putOrder(order);
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
        cacheService.putOrder(order);
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
        cacheService.evictOrder(orderId);
        return order;
    }
}
