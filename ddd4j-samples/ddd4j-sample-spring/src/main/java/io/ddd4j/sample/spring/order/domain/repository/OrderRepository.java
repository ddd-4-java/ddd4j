package io.ddd4j.sample.spring.order.domain.repository;

import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.sample.spring.order.domain.model.Order;
import io.ddd4j.spring.annotation.DomainRepository;

import java.util.List;
import java.util.Optional;

/**
 * 订单聚合仓储接口。
 *
 * <p>使用 {@link DomainRepository} 标注：ddd4j 自动融合 Spring {@code @Repository} 元注解，
 * 使接口本身被 Spring 容器识别为 Repository Bean。
 * 真正的存储实现由 {@code InMemoryOrderRepository} 提供。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@DomainRepository
public interface OrderRepository extends Repository<Order, String> {

    /**
     * 根据订单编号查询订单。
     *
     * @param orderNo 订单编号
     * @return 查询结果
     */
    Optional<Order> findByOrderNo(String orderNo);

    /**
     * 列出全部订单（演示用，生产中走 ReadModel）。
     *
     * @return 全部订单聚合列表
     */
    List<Order> findAll();
}
