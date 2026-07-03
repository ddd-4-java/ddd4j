package io.ddd4j.sample.richmodel.order.infrastructure.persistence;

import io.ddd4j.core.ddd.model.DomainObjectMapper;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.richmodel.order.domain.model.Money;
import io.ddd4j.sample.richmodel.order.domain.model.Order;
import io.ddd4j.sample.richmodel.order.domain.model.OrderLine;
import io.ddd4j.sample.richmodel.order.domain.model.OrderStatus;
import io.ddd4j.sample.richmodel.order.domain.repository.OrderRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于内存的订单仓库实现（保持 Model/PO 分离）。
 *
 * <p>使用 ConcurrentHashMap 存储订单持久化对象，
 * 通过 {@link DomainObjectMapper} 实现领域模型与持久化对象的双向转换。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class InMemoryOrderRepository implements OrderRepository, DomainObjectMapper<Order, OrderPO> {

    private final ConcurrentMap<String, OrderPO> rows = new ConcurrentHashMap<>();

    @Override
    public Optional<Order> findById(String id) {
        if (StrKit.isBlank(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(id)).map(this::toModel);
    }

    @Override
    public Optional<Order> findByOrderNo(String orderNo) {
        if (StrKit.isBlank(orderNo)) {
            return Optional.empty();
        }
        return rows.values().stream()
                .filter(row -> Objects.equals(orderNo, row.getOrderNo()))
                .findFirst()
                .map(this::toModel);
    }

    @Override
    public Order save(Order aggregate) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        rows.put(aggregate.id(), toPersistenceObject(aggregate));
        return aggregate;
    }

    @Override
    public void deleteById(String id) {
        if (StrKit.isNotBlank(id)) {
            rows.remove(id);
        }
    }

    @Override
    public Order toModel(OrderPO persistenceObject) {
        Objects.requireNonNull(persistenceObject, "persistenceObject must not be null");
        List<OrderLine> lines = Optional.ofNullable(persistenceObject.getLines())
                .orElseGet(List::of)
                .stream()
                .map(this::toLineModel)
                .toList();
        return new Order(
                persistenceObject.getId(),
                persistenceObject.getOrderNo(),
                persistenceObject.getBuyerId(),
                persistenceObject.getBuyerName(),
                OrderStatus.valueOf(persistenceObject.getStatus()),
                lines
        );
    }

    @Override
    public OrderPO toPersistenceObject(Order model) {
        Objects.requireNonNull(model, "model must not be null");
        Money totalAmount = model.totalAmount();
        return OrderPO.builder()
                .id(model.id())
                .orderNo(model.orderNo())
                .buyerId(model.buyerId())
                .buyerName(model.buyerName())
                .status(model.status().name())
                .totalAmount(totalAmount.amount())
                .currency(totalAmount.currency())
                .lines(model.lines().stream().map(this::toLinePersistenceObject).toList())
                .build();
    }

    private OrderLine toLineModel(OrderLinePO persistenceObject) {
        Objects.requireNonNull(persistenceObject, "persistenceObject must not be null");
        return new OrderLine(
                persistenceObject.getId(),
                persistenceObject.getProductId(),
                persistenceObject.getProductName(),
                persistenceObject.getQuantity(),
                new Money(persistenceObject.getUnitPrice(), persistenceObject.getCurrency())
        );
    }

    private OrderLinePO toLinePersistenceObject(OrderLine model) {
        Objects.requireNonNull(model, "model must not be null");
        return OrderLinePO.builder()
                .id(model.id())
                .productId(model.productId())
                .productName(model.productName())
                .quantity(model.quantity())
                .unitPrice(model.unitPrice().amount())
                .currency(model.unitPrice().currency())
                .build();
    }
}
