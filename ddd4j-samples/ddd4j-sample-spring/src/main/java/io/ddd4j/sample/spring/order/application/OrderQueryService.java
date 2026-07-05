package io.ddd4j.sample.spring.order.application;

import io.ddd4j.sample.spring.order.domain.model.Order;
import io.ddd4j.sample.spring.order.domain.repository.OrderRepository;
import io.ddd4j.spring.annotation.ApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Objects;

/**
 * 订单查询服务（CQRS 读侧）。
 *
 * <p>与 {@link OrderApplicationService}（写侧）分离，演示 CQRS 模式：
 * <ul>
 *   <li>写侧：{@code OrderApplicationService} 编排命令，修改聚合状态</li>
 *   <li>读侧：{@code OrderQueryService} 提供独立的查询能力，不修改聚合</li>
 * </ul>
 *
 * <p>实际生产中，读侧可独立建模（ReadModel / View），
 * 本示例为简化演示，读侧仍查询同一仓储。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@ApplicationService
public class OrderQueryService {

    private final OrderRepository repository;

    /**
     * 构造函数。
     *
     * @param repository 订单仓储
     */
    @Autowired
    public OrderQueryService(OrderRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /**
     * 分页查询订单（简化实现：基于 findAll 内存分页）。
     *
     * @param page     页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public PageResult listOrders(int page, int pageSize) {
        List<Order> all = repository.findAll();
        int total = all.size();
        int fromIndex = Math.min((page - 1) * pageSize, total);
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<Order> items = all.subList(fromIndex, toIndex);
        return new PageResult(items, total, page, pageSize);
    }

    /**
     * 查询订单详情。
     *
     * @param orderId 订单 ID
     * @return 订单聚合
     */
    public Order getOrderDetail(String orderId) {
        return repository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
    }

    /**
     * 分页查询结果。
     *
     * @param items    当前页数据
     * @param total    总数
     * @param page     当前页码
     * @param pageSize 每页大小
     */
    public record PageResult(List<Order> items, int total, int page, int pageSize) {
    }
}
