package io.ddd4j.sample.spring.order.web;

import io.ddd4j.core.api.R;
import io.ddd4j.sample.spring.order.application.OrderQueryService;
import io.ddd4j.sample.spring.order.application.OrderQueryService.PageResult;
import io.ddd4j.sample.spring.order.web.dto.OrderResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 订单 CQRS 查询控制器（读侧）。
 *
 * <p>与 {@link OrderController}（写侧）分离，演示 CQRS 模式：
 * <ul>
 *   <li>写侧：POST /orders、POST /orders/{id}/pay 等修改操作</li>
 *   <li>读侧：GET /orders 分页查询、GET /orders/{id}/detail 详情查询</li>
 * </ul>
 *
 * <p>REST 端点：
 * <ul>
 *   <li>GET /orders                分页查询</li>
 *   <li>GET /orders/{id}/detail    订单详情</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RestController
@RequestMapping("/orders")
public class OrderQueryController {

    private final OrderQueryService orderQueryService;

    /**
     * 构造函数。
     *
     * @param orderQueryService 订单查询服务
     */
    @Autowired
    public OrderQueryController(OrderQueryService orderQueryService) {
        this.orderQueryService = orderQueryService;
    }

    /**
     * 分页查询订单。
     *
     * @param page     页码（默认 1）
     * @param pageSize 每页大小（默认 10）
     * @return R 响应，data 包含分页数据
     */
    @GetMapping
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
     * 查询订单详情。
     *
     * @param id 订单 ID
     * @return R 响应
     */
    @GetMapping("/{id}/detail")
    public R<OrderResponse> detail(@PathVariable String id) {
        return R.ok(OrderResponse.from(orderQueryService.getOrderDetail(id)));
    }
}
