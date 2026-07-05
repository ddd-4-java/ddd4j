package io.ddd4j.sample.spring.cqrs.order.web;

import io.ddd4j.core.api.R;
import io.ddd4j.sample.spring.cqrs.cache.OrderCacheService;
import io.ddd4j.sample.spring.cqrs.order.application.OrderQueryService;
import io.ddd4j.sample.spring.cqrs.order.application.OrderQueryService.PageResult;
import io.ddd4j.sample.spring.cqrs.order.web.dto.OrderResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 订单 CQRS 读侧控制器 - 缓存增强版本（Spring Boot）。
 *
 * <p>本控制器是 CQRS 示例相对于基线 {@link OrderQueryController} 的增强：
 * <ul>
 *   <li>分页列表通过仓储内存分页实现</li>
 *   <li>订单统计走 {@link OrderCacheService} 缓存（Caffeine 本地缓存）</li>
 *   <li>买家订单计数同样走缓存</li>
 * </ul>
 *
 * <p>REST 端点（CQRS 风格命名空间）：
 * <ul>
 *   <li>GET /api/orders/query/list      分页列表</li>
 *   <li>GET /api/orders/query/stats     订单统计（缓存）</li>
 *   <li>GET /api/orders/query/buyer/{buyerId}/count  买家订单计数（缓存）</li>
 *   <li>GET /api/orders/query/detail/{id}           订单详情</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RestController
@RequestMapping("/api/orders/query")
public class OrderCQRSQueryController {

    private final OrderQueryService orderQueryService;
    private final OrderCacheService orderCacheService;

    /**
     * 构造函数（Spring 注入）。
     *
     * @param orderQueryService 订单查询服务
     * @param orderCacheService 订单缓存服务
     */
    @Autowired
    public OrderCQRSQueryController(OrderQueryService orderQueryService, OrderCacheService orderCacheService) {
        this.orderQueryService = orderQueryService;
        this.orderCacheService = orderCacheService;
    }

    /**
     * 分页查询订单列表（CQRS 读侧）。
     *
     * @param page     页码（默认 1）
     * @param pageSize 每页大小（默认 10）
     * @return R 响应
     */
    @GetMapping("/list")
    public R<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResult result = orderQueryService.listOrders(page, pageSize);
        List<OrderResponse> items = result.items().stream()
                .map(OrderResponse::from)
                .toList();
        return R.ok(Map.of(
                "items", items,
                "total", result.total(),
                "page", result.page(),
                "pageSize", result.pageSize()
        ));
    }

    /**
     * 订单统计（缓存优先）。
     *
     * @return 订单统计
     */
    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        return R.ok(orderCacheService.getOrderStats());
    }

    /**
     * 买家订单计数（缓存优先）。
     *
     * @param buyerId 买家 ID
     * @return 订单数量
     */
    @GetMapping("/buyer/{buyerId}/count")
    public R<Map<String, Object>> buyerCount(@PathVariable String buyerId) {
        long count = orderCacheService.getBuyerOrderCount(buyerId);
        return R.ok(Map.of("buyerId", buyerId, "count", count));
    }

    /**
     * 查询订单详情。
     *
     * @param id 订单 ID
     * @return R 响应
     */
    @GetMapping("/detail/{id}")
    public R<OrderResponse> detail(@PathVariable String id) {
        return R.ok(OrderResponse.from(orderQueryService.getOrderDetail(id)));
    }
}