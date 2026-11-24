package io.ddd4j.sample.dropwizard.cqrs.persistent;

import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.application.OrderReadModelPort;
import io.ddd4j.sample.order.domain.OrderQuery;
import java.util.List;
import java.util.Optional;

/** CQRS 写侧禁用同步投影；读取只委托独立查询仓储，HTTP 不调用应用服务的回退查询。
 */
public final class DeferredOrderReadModel implements OrderReadModelPort {
    private final OrderReadModelPort queries;
    /**
     * @param queries 独立查询仓储
     */
    public DeferredOrderReadModel(OrderReadModelPort queries) { this.queries = queries; }
    @Override public void project(OrderReadModel order) {
        // 共享应用服务仍负责原子写入 Order/Outbox，查询仓储由持久化事件中继后的 Runner 更新。
    }
    @Override public Optional<OrderReadModel> findProjectionById(String id) { return queries.findProjectionById(id); }
    @Override public List<OrderReadModel> query(OrderQuery query) { return queries.query(query); }
}
