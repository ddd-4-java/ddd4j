package io.ddd4j.sample.spring.order.web;

import io.ddd4j.core.api.R;
import io.ddd4j.sample.spring.order.application.AddOrderLineCommand;
import io.ddd4j.sample.spring.order.application.CreateOrderCommand;
import io.ddd4j.sample.spring.order.application.OrderApplicationService;
import io.ddd4j.sample.spring.order.domain.model.Money;
import io.ddd4j.sample.spring.order.domain.model.Order;
import io.ddd4j.sample.spring.order.web.dto.AddOrderLineRequest;
import io.ddd4j.sample.spring.order.web.dto.CreateOrderRequest;
import io.ddd4j.sample.spring.order.web.dto.OrderResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订单 REST 控制器（写侧）。
 *
 * <p>返回 ddd4j-core 提供的统一响应包装 {@link R}，
 * 由 ddd4j-web-webmvc 的 {@code GlobalRestExceptionAdvice} / {@code GlobalResponseRAdvice}
 * 自动完成全局异常处理与响应包装。
 *
 * <p>REST 端点：
 * <ul>
 *   <li>POST /orders                    创建订单</li>
 *   <li>GET  /orders/{id}              查询订单</li>
 *   <li>POST /orders/{id}/lines        添加订单行</li>
 *   <li>POST /orders/{id}/pay          支付订单</li>
 *   <li>POST /orders/{id}/ship         发货订单</li>
 *   <li>POST /orders/{id}/cancel       取消订单</li>
 *   <li>GET  /orders/{id}/discount     预览折扣</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderApplicationService orderApplicationService;

    /**
     * 构造函数。
     *
     * @param orderApplicationService 订单应用服务
     */
    @Autowired
    public OrderController(OrderApplicationService orderApplicationService) {
        this.orderApplicationService = orderApplicationService;
    }

    /**
     * 创建订单。
     *
     * @param request 创建订单请求
     * @return 统一 R 响应，data 为订单聚合的 DTO
     */
    @PostMapping
    public R<OrderResponse> create(@RequestBody CreateOrderRequest request) {
        Order order = orderApplicationService.createDraft(
                new CreateOrderCommand(request.orderNo(), request.buyerId(), request.buyerName())
        );
        return R.ok("order created", OrderResponse.from(order));
    }

    /**
     * 查询订单。
     *
     * @param id 订单 ID
     * @return R 响应
     */
    @GetMapping("/{id}")
    public R<OrderResponse> findById(@PathVariable String id) {
        Order order = orderApplicationService.findById(id);
        return R.ok(OrderResponse.from(order));
    }

    /**
     * 添加订单行。
     *
     * @param id      订单 ID
     * @param request 添加订单行请求
     * @return R 响应
     */
    @PostMapping("/{id}/lines")
    public R<OrderResponse> addLine(@PathVariable String id, @RequestBody AddOrderLineRequest request) {
        Order order = orderApplicationService.addLine(new AddOrderLineCommand(
                id, request.goodsId(), request.goodsName(), request.quantity(), request.unitPrice()
        ));
        return R.ok("order line added", OrderResponse.from(order));
    }

    /**
     * 支付订单。
     *
     * @param id 订单 ID
     * @return R 响应
     */
    @PostMapping("/{id}/pay")
    public R<OrderResponse> pay(@PathVariable String id) {
        Order order = orderApplicationService.pay(id);
        return R.ok("order paid", OrderResponse.from(order));
    }

    /**
     * 发货订单。
     *
     * @param id 订单 ID
     * @return R 响应
     */
    @PostMapping("/{id}/ship")
    public R<OrderResponse> ship(@PathVariable String id) {
        Order order = orderApplicationService.ship(id);
        return R.ok("order shipped", OrderResponse.from(order));
    }

    /**
     * 取消订单。
     *
     * @param id 订单 ID
     * @return R 响应
     */
    @PostMapping("/{id}/cancel")
    public R<OrderResponse> cancel(@PathVariable String id) {
        Order order = orderApplicationService.cancel(id);
        return R.ok("order cancelled", OrderResponse.from(order));
    }

    /**
     * 预览订单折扣（演示领域服务调用）。
     *
     * @param id 订单 ID
     * @return R 响应
     */
    @GetMapping("/{id}/discount")
    public R<DiscountView> previewDiscount(@PathVariable String id) {
        Money discounted = orderApplicationService.previewDiscount(id);
        return R.ok(new DiscountView(discounted.amount().toPlainString(), discounted.currency()));
    }

    /**
     * 折扣预览响应。
     */
    public record DiscountView(String amount, String currency) {
    }
}
