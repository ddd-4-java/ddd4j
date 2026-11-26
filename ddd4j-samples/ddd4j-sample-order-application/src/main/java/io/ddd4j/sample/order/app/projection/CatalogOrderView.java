package io.ddd4j.sample.order.app.projection;

import io.ddd4j.sample.order.app.projection.TradeOrderSnapshot;
import java.util.Collections;
import io.ddd4j.core.cqrs.query.QueryHandler;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.sample.order.client.query.TradeOrderQuery;
import io.ddd4j.sample.order.domain.model.TradeOrder;
import java.util.Collection;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;

/** 按租户和订单号从已提交的四表数据重建独立查询快照。 */
@RequiredArgsConstructor
public final class CatalogOrderView
        implements ProjectionView<TradeOrderQuery>, QueryHandler<ProjectedTradeOrderQuery, TradeOrderSnapshot> {
    private final Function<TradeOrderQuery, TradeOrder> readCommittedOrder;
    private final Map<String, ConcurrentHashMap<String, TradeOrderSnapshot>> orders = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "catalog-orders";
    }

    @Override
    public String getCron() {
        return "";
    }

    @Override
    public Collection<String> getEventTypes() {
        return Collections.emptySet();
    }

    @Override
    public void handleEvents(Collection<TradeOrderQuery> requests) {
        requests.forEach(this::rebuild);
    }

    /**
     * 从已提交的订单快照重建读模型。
     *
     * @param request 指定租户和订单编号的查询
     * @return 更新后的订单投影
     */
    public TradeOrderSnapshot rebuild(TradeOrderQuery request) {
        String tenant = Objects.requireNonNull(request.getTenantId());
        String orderNo = Objects.requireNonNull(request.getOrderNo());
        // 同一业务键必须先获得视图更新权，再读取数据库，避免旧查询结果延迟回写。
        return orders.computeIfAbsent(tenant, key -> new ConcurrentHashMap<>())
                .compute(orderNo, (key, previous) -> TradeOrderSnapshot.from(readCommittedOrder.apply(request)));
    }

    @Override
    public Class<ProjectedTradeOrderQuery> queryType() {
        return ProjectedTradeOrderQuery.class;
    }

    @Override
    public TradeOrderSnapshot handle(ProjectedTradeOrderQuery query) {
        return Optional.ofNullable(orders.get(query.getTenantId()))
                .map(tenantOrders -> tenantOrders.get(query.getOrderNo()))
                .orElseThrow(() -> new NoSuchElementException("订单尚未投影"));
    }
}
