package io.ddd4j.sample.javalin.cqrs.order.infrastructure;

import java.util.stream.Collectors;
import io.ddd4j.core.ddd.model.DomainObjectMapper;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.javalin.cqrs.order.domain.model.Money;
import io.ddd4j.sample.javalin.cqrs.order.domain.model.Order;
import io.ddd4j.sample.javalin.cqrs.order.domain.model.OrderLine;
import io.ddd4j.sample.javalin.cqrs.order.domain.model.OrderStatus;
import io.ddd4j.sample.javalin.cqrs.order.domain.repository.OrderRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于内存的订单仓库实现（第二轨：充血模型）。
 *
 * <p>使用 {@link ConcurrentHashMap} 存储 {@link OrderPO} 持久化对象，
 * 通过实现 {@link DomainObjectMapper}{@code <Order, OrderPO>} 在充血聚合与 PO 间双向转换。
 *
 * <p>特点（与第三轨 GoodsRepository 对比）：
 * <ul>
 *   <li><b>有 Model/PO 分离</b>：Order 不直接是 PO，避免充血聚合被 ORM 注解污染</li>
 *   <li><b>有 DomainObjectMapper</b>：仓储实现双接口完成映射</li>
 *   <li><b>不实现 Repository</b>：第二轨按"业务行为驱动"而非"条件查询驱动"</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class InMemoryOrderRepository implements OrderRepository, DomainObjectMapper<Order, OrderPO> {

    private final ConcurrentMap<String, OrderPO> rows = new ConcurrentHashMap<>();

    // ========================= OrderRepository =========================

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
    public List<Order> findAll() {
        return rows.values().stream().map(this::toModel).collect(java.util.stream.Collectors.toList());
    }

    @Override
    public Order save(Order aggregate) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        rows.put(aggregate.getId(), toPersistenceObject(aggregate));
        return aggregate;
    }

    @Override
    public void deleteById(String id) {
        if (StrKit.isNotBlank(id)) {
            rows.remove(id);
        }
    }

    // ========================= DomainObjectMapper<Order, OrderPO> =========================

    @Override
    public Order toModel(OrderPO persistenceObject) {
        Objects.requireNonNull(persistenceObject, "persistenceObject must not be null");
        List<OrderLine> lines = Optional.ofNullable(persistenceObject.getLines())
                .orElseGet(List::of)
                .stream()
                .map(this::toLineModel)
                .collect(java.util.stream.Collectors.toList());
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
                .id(model.getId())
                .orderNo(model.getOrderNo())
                .buyerId(model.getBuyerId())
                .buyerName(model.getBuyerName())
                .status(model.getStatus().getName())
                .totalAmount(totalAmount.getAmount())
                .currency(totalAmount.getCurrency())
                .lines(model.lines().stream().map(this::toLinePersistenceObject).collect(java.util.stream.Collectors.toList()))
                .build();
    }

    private OrderLine toLineModel(OrderLinePO persistenceObject) {
        Objects.requireNonNull(persistenceObject, "persistenceObject must not be null");
        return new OrderLine(
                persistenceObject.getId(),
                persistenceObject.getGoodsId(),
                persistenceObject.getGoodsName(),
                persistenceObject.getQuantity(),
                new Money(persistenceObject.getUnitPrice(), persistenceObject.getCurrency())
        );
    }

    private OrderLinePO toLinePersistenceObject(OrderLine model) {
        Objects.requireNonNull(model, "model must not be null");
        return OrderLinePO.builder()
                .id(model.getId())
                .goodsId(model.goodsId())
                .goodsName(model.goodsName())
                .quantity(model.quantity())
                .unitPrice(model.unitPrice().getAmount())
                .currency(model.unitPrice().getCurrency())
                .build();
    }
}
