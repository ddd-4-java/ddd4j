package io.ddd4j.sample.spring.order.application;

import io.ddd4j.sample.spring.order.domain.model.Money;
import io.ddd4j.sample.spring.order.domain.model.Order;
import io.ddd4j.sample.spring.order.domain.repository.OrderRepository;
import io.ddd4j.sample.spring.order.domain.service.OrderDomainService;
import io.ddd4j.spring.annotation.ApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 订单应用服务：编排业务用例，领域规则下沉到 Order 聚合中。
 *
 * <p>使用 {@link ApplicationService} 标注：ddd4j 自动融合 Spring {@code @Service} 元注解，
 * 自动被 Spring 容器扫描注册为 Service Bean。
 *
 * <p>本服务演示：
 * <ul>
 *   <li>依赖注入 {@link OrderRepository} 与 {@link OrderDomainService}</li>
 *   <li>事务边界（{@code @Transactional}）</li>
 *   <li>触发 ddd4j 领域事件发布（通过 {@link io.ddd4j.spring.event.SpringDomainEventPublisher} 桥接）</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@ApplicationService
public class OrderApplicationService {

    private final OrderRepository repository;
    private final OrderDomainService domainService;

    /**
     * 构造函数（推荐 Spring 构造器注入）。
     *
     * @param repository    订单仓储
     * @param domainService 订单领域服务
     */
    @Autowired
    public OrderApplicationService(OrderRepository repository, OrderDomainService domainService) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.domainService = Objects.requireNonNull(domainService, "domainService must not be null");
    }

    /**
     * 创建草稿订单。
     *
     * @param command 创建订单命令
     * @return 创建的订单聚合
     */
    @Transactional
    public Order createDraft(CreateOrderCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        log.info("Creating draft order: orderNo={}, buyerId={}", command.orderNo(), command.buyerId());
        Order order = Order.draft(command.orderNo(), command.buyerId(), command.buyerName());
        repository.save(order);
        publishDomainEvents(order);
        return order;
    }

    /**
     * 添加订单行。
     *
     * @param command 添加订单行命令
     * @return 更新后的订单聚合
     */
    @Transactional
    public Order addLine(AddOrderLineCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Order order = repository.findById(command.orderId())
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + command.orderId()));
        order.addLine(command.goodsId(), command.goodsName(), command.quantity(),
                new Money(command.unitPrice(), "CNY"));
        repository.save(order);
        publishDomainEvents(order);
        return order;
    }

    /**
     * 支付订单。
     *
     * @param orderId 订单 ID
     * @return 支付后的订单聚合
     */
    @Transactional
    public Order pay(String orderId) {
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
        order.pay();
        repository.save(order);
        publishDomainEvents(order);
        return order;
    }

    /**
     * 发货订单。
     *
     * @param orderId 订单 ID
     * @return 发货后的订单聚合
     */
    @Transactional
    public Order ship(String orderId) {
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
        order.ship();
        repository.save(order);
        publishDomainEvents(order);
        return order;
    }

    /**
     * 取消订单。
     *
     * @param orderId 订单 ID
     * @return 取消后的订单聚合
     */
    @Transactional
    public Order cancel(String orderId) {
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
        order.cancel();
        repository.save(order);
        publishDomainEvents(order);
        return order;
    }

    /**
     * 查询订单。
     *
     * @param orderId 订单 ID
     * @return 订单聚合
     */
    public Order findById(String orderId) {
        return repository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
    }

    /**
     * 根据订单号查询订单。
     *
     * @param orderNo 订单编号
     * @return 订单聚合
     */
    public Order findByOrderNo(String orderNo) {
        return repository.findByOrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("order not found by orderNo: " + orderNo));
    }

    /**
     * 预览订单折扣（演示领域服务调用）。
     *
     * @param orderId 订单 ID
     * @return 折后金额
     */
    public Money previewDiscount(String orderId) {
        Order order = findById(orderId);
        return domainService.previewDiscount(order);
    }

    /**
     * 列出全部订单（演示查询）。
     *
     * @return 全部订单聚合列表
     */
    public List<Order> listAll() {
        return repository.findAll();
    }

    /**
     * 发布聚合根上暂存的领域事件并清空。
     *
     * <p>ddd4j-core 的 {@code DomainEvent.publish()} 通过 SPI 查找
     * {@code SpringDomainEventPublisher}，再由 Spring {@code ApplicationEventPublisher}
     * 分发到 {@code @EventListener} 监听器。
     *
     * @param order 订单聚合
     */
    private void publishDomainEvents(Order order) {
        order.domainEvents().forEach(event -> {
            log.debug("Publishing domain event: {}", event.getClass().getSimpleName());
            event.publish();
        });
        order.clearDomainEvents();
    }
}
