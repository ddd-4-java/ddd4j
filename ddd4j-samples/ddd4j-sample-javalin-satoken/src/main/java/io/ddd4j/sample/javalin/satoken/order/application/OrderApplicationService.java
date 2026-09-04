package io.ddd4j.sample.javalin.satoken.order.application;

import io.ddd4j.sample.javalin.satoken.order.domain.model.Money;
import io.ddd4j.sample.javalin.satoken.order.domain.model.Order;
import io.ddd4j.sample.javalin.satoken.order.domain.repository.OrderRepository;
import io.ddd4j.sample.javalin.satoken.order.domain.service.OrderDomainService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;

/**
 * 订单应用服务：编排业务用例，领域规则下沉到 Order 聚合中。
 *
 * <p>Quarkus CDI 管理（{@link ApplicationScoped}），
 * 通过构造器注入 {@link OrderRepository} 与 {@link OrderDomainService}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@ApplicationScoped
public class OrderApplicationService {

    private final OrderRepository repository;
    private final OrderDomainService domainService;

    @Inject
    public OrderApplicationService(OrderRepository repository, OrderDomainService domainService) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.domainService = Objects.requireNonNull(domainService, "domainService must not be null");
    }

    public Order createDraft(CreateOrderCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        log.info("Creating draft order: orderNo={}, buyerId={}", command.orderNo(), command.buyerId());
        Order order = Order.draft(command.orderNo(), command.buyerId(), command.buyerName());
        repository.save(order);
        publishDomainEvents(order);
        return order;
    }

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

    public Order pay(String orderId) {
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
        order.pay();
        repository.save(order);
        publishDomainEvents(order);
        return order;
    }

    public Order ship(String orderId) {
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
        order.ship();
        repository.save(order);
        publishDomainEvents(order);
        return order;
    }

    public Order cancel(String orderId) {
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
        order.cancel();
        repository.save(order);
        publishDomainEvents(order);
        return order;
    }

    public Order findById(String orderId) {
        return repository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
    }

    public Order findByOrderNo(String orderNo) {
        return repository.findByOrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("order not found by orderNo: " + orderNo));
    }

    public Money previewDiscount(String orderId) {
        Order order = findById(orderId);
        return domainService.previewDiscount(order);
    }

    public List<Order> listAll() {
        return repository.findAll();
    }

    private void publishDomainEvents(Order order) {
        order.domainEvents().forEach(event -> {
            log.debug("Publishing domain event: {}", event.getClass().getSimpleName());
            event.publish();
        });
        order.clearDomainEvents();
    }
}