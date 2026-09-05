package io.ddd4j.sample.quarkus.shiro.order.domain.repository;

import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.sample.quarkus.shiro.order.domain.model.Order;

import java.util.List;
import java.util.Optional;

/**
 * 订单聚合仓库接口。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface OrderRepository extends Repository<Order, String> {

    Optional<Order> findByOrderNo(String orderNo);

    List<Order> findAll();
}