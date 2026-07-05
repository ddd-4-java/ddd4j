package io.ddd4j.sample.spring.shiro.order.domain.repository;

import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.sample.spring.shiro.order.domain.model.Order;

import java.util.List;
import java.util.Optional;

/**
 * 订单聚合仓库接口。
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

    /**
     * 查询全部订单聚合。
     *
     * @return 订单列表
     */
    List<Order> findAll();
}
