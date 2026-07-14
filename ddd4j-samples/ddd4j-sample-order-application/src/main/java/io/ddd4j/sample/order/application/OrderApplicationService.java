package io.ddd4j.sample.order.application;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.sample.order.domain.Money;
import io.ddd4j.sample.order.domain.Order;
import io.ddd4j.sample.order.domain.OrderQuery;
import io.ddd4j.sample.order.domain.OrderRepository;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
public class OrderApplicationService {

    private static final Duration PAYMENT_IDEMPOTENCY_TTL = Duration.ofMinutes(10);

    private final OrderRepository repository;
    private final OutboxPort outbox;
    private final OrderReadModelPort readModels;
    private final IdempotencyPort idempotency;

    public OrderApplicationService(OrderRepository repository, OutboxPort outbox,
                                   OrderReadModelPort readModels, IdempotencyPort idempotency) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.outbox = Objects.requireNonNull(outbox, "outbox must not be null");
        this.readModels = Objects.requireNonNull(readModels, "readModels must not be null");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency must not be null");
    }

    public Order create(CreateOrderCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Order order = Order.draft(command.orderNo(), command.buyerId(), command.buyerName());
        persist(order);
        return order;
    }

    public Order addLine(AddOrderLineCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Order order = load(command.orderId());
        order.addLine(command.goodsId(), command.goodsName(), command.quantity(), Money.cny(command.unitPrice()));
        persist(order);
        return order;
    }

    public Order pay(String orderId, String idempotencyKey) {
        String key = "order:payment:" + Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        if (!idempotency.acquire(key, PAYMENT_IDEMPOTENCY_TTL)) {
            return load(orderId);
        }
        try {
            Order order = load(orderId);
            order.pay();
            persist(order);
            idempotency.complete(key, order.id());
            return order;
        } catch (RuntimeException exception) {
            idempotency.release(key);
            throw exception;
        }
    }

    public Order cancel(String orderId) {
        Order order = load(orderId);
        order.cancel();
        persist(order);
        return order;
    }

    public Order ship(String orderId) {
        Order order = load(orderId);
        order.ship();
        persist(order);
        return order;
    }

    public OrderReadModel find(String orderId) {
        return readModels.findProjectionById(orderId).orElseGet(() -> toReadModel(load(orderId)));
    }

    public OrderReadModel findByOrderNo(String orderNo) {
        return repository.findByOrderNo(orderNo)
                .map(this::toReadModel)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderNo));
    }

    public List<OrderReadModel> query(OrderQuery query) {
        return readModels.query(Objects.requireNonNull(query, "query must not be null"));
    }

    private Order load(String orderId) {
        return repository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
    }

    private void persist(Order order) {
        List<DomainEvent<?>> events = List.copyOf(order.domainEvents());
        repository.save(order);
        outbox.append(events.stream().map(event -> new OutboxMessage(UUID.randomUUID().toString(), order.id(),
                event.getClass().getName(), event, Instant.now())).toList());
        readModels.project(toReadModel(order));
        order.clearDomainEvents();
        log.debug("Persisted order {} with {} outbox events", order.id(), events.size());
    }

    private OrderReadModel toReadModel(Order order) {
        return new OrderReadModel(order.id(), order.orderNo(), order.buyerId(), order.buyerName(),
                order.status(), order.totalAmount().amount());
    }
}
