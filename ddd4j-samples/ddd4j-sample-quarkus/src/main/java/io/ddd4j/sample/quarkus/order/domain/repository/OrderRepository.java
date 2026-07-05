package io.ddd4j.sample.quarkus.order.domain.repository;

import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.sample.quarkus.order.domain.model.Order;

import java.util.Optional;

/**
 * 订单聚合仓库接口。
 *
 * <p>继承自 ddd4j-core 的 {@link Repository}，业务方可按需扩展查询方法。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface OrderRepository extends Repository<Order, String> {

    /**
     * 根据订单编号查询订单。
     *
     * @param orderNo 订单编号
     * @return 查询结果
     */
    Optional<Order> findByOrderNo(String orderNo);
}