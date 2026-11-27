package io.ddd4j.sample.order.app.projection;

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
    /** \u6839\u636e\u79df\u6237\u548c\u8ba2\u5355\u53f7\u52a0\u8f7d\u5df2\u63d0\u4ea4\u8ba2\u5355\u7684\u67e5\u8be2\u51fd\u6570\u3002 */
    private final Function<TradeOrderQuery, TradeOrder> readCommittedOrder;
    /** \u6309\u79df\u6237\u548c\u8ba2\u5355\u53f7\u5206\u533a\u7684\u8ba2\u5355\u5feb\u7167\u3002 */
    private final Map<String, ConcurrentHashMap<String, TradeOrderSnapshot>> orders = new ConcurrentHashMap<>();

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return "catalog-orders";
    }

    /** {@inheritDoc} */
    @Override
    public String getCron() {
        return "";
    }

    /** {@inheritDoc} */
    @Override
    public Collection<String> getEventTypes() {
        return Set.of();
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public Class<ProjectedTradeOrderQuery> queryType() {
        return ProjectedTradeOrderQuery.class;
    }

    /** {@inheritDoc} */
    @Override
    public TradeOrderSnapshot handle(ProjectedTradeOrderQuery query) {
        return Optional.ofNullable(orders.get(query.getTenantId()))
                .map(tenantOrders -> tenantOrders.get(query.getOrderNo()))
                .orElseThrow(() -> new NoSuchElementException("订单尚未投影"));
    }
}
