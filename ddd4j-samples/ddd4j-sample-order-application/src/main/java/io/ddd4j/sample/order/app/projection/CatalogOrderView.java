package io.ddd4j.sample.order.app.projection;

import io.ddd4j.core.cqrs.query.QueryHandler;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.sample.order.client.query.TradeOrderQuery;
import io.ddd4j.sample.order.domain.model.TradeOrder;
import lombok.RequiredArgsConstructor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 按租户和订单号从已提交的四表数据重建独立查询快照。
 */
@RequiredArgsConstructor
public final class CatalogOrderView
        implements ProjectionView<TradeOrderQuery>, QueryHandler<ProjectedTradeOrderQuery, TradeOrderSnapshot> {
    /**
     * 根据租户和订单号加载已提交订单的查询函数。
     */
    private final Function<TradeOrderQuery, TradeOrder> readCommittedOrder;
    /**
     * 按租户和订单号分区的订单快照。
     */
    private final Map<String, ConcurrentHashMap<String, TradeOrderSnapshot>> orders = new ConcurrentHashMap<>();

    /**
     * 返回当前配置、视图或处理器的名称。
     *
     * @return 实际返回<code>"catalog-orders"</code>，供调用方取得当前值
     */
    @Override
    public String getName() {
        return "catalog-orders";
    }

    /**
     * 返回投影视图配置的调度表达式。
     *
     * @return 当前视图使用的 Cron 表达式
     */
    @Override
    public String getCron() {
        return "";
    }

    /**
     * 返回投影或处理器关心的事件类型。
     *
     * <p>此实现调用 <code>Set.of()</code>。</p>
     *
     * @return 实际返回<code>Set.of()</code>
     */
    @Override
    public Collection<String> getEventTypes() {
        return Set.of();
    }

    /**
     * 处理本批投影事件。
     *
     * @param requests 传给 <code>requests.forEach(this::rebuild)</code>
     */
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

    /**
     * 声明当前查询处理器接受的查询类型。
     *
     * @return 本处理器接受的查询类型
     */
    @Override
    public Class<ProjectedTradeOrderQuery> queryType() {
        return ProjectedTradeOrderQuery.class;
    }

    /**
     * 处理调用方传入的消息或请求。
     *
     * <p>此实现调用 <code>Optional.ofNullable(orders.get(query.getTenantId()))</code>。</p>
     *
     * @param query 查询对象
     * @return 查询结果
     */
    @Override
    public TradeOrderSnapshot handle(ProjectedTradeOrderQuery query) {
        return Optional.ofNullable(orders.get(query.getTenantId()))
                .map(tenantOrders -> tenantOrders.get(query.getOrderNo()))
                .orElseThrow(() -> new NoSuchElementException("订单尚未投影"));
    }
}
