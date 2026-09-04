package io.ddd4j.sample.javalin.cqrs.order.domain.repository;

import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.sample.javalin.cqrs.order.domain.model.Order;

import java.util.List;
import java.util.Optional;

/**
 * 订单聚合仓储接口（第二轨：充血模型）。
 *
 * <p>继承 ddd4j-core 的 {@link Repository} 接口，标识该仓储操作 {@link Order} 聚合根。
 * 实现类（{@code InMemoryOrderRepository}）需提供：
 * <ul>
 *   <li>{@link #findById(Object)} - 按主键查询</li>
 *   <li>{@link #findByOrderNo(String)} - 按订单编号查询（业务唯一键）</li>
 *   <li>{@link #findAll()} - 列出全部订单（CQRS 缓存统计用）</li>
 *   <li>{@link #save(Order)} - 保存或更新</li>
 *   <li>{@link #deleteById(Object)} - 按主键删除（继承自默认方法）</li>
 *   <li>{@link #existsById(Object)} - 判断存在（继承自默认方法）</li>
 * </ul>
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
     * 列出全部订单（CQRS 缓存统计用）。
     *
     * @return 全部订单聚合列表
     */
    List<Order> findAll();
}