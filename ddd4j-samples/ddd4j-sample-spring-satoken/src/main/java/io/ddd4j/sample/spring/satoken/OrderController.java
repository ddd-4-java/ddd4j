package io.ddd4j.sample.spring.satoken;

import io.ddd4j.annotation.api.RawResponse;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.domain.OrderQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** HTTP 边界仅委托共享 Order 服务，不复制领域或应用规则。 */
@RestController
public class OrderController {
    private final OrderApplicationService orders;
    /**
     * @param orders 当前容器中的共享应用服务
     */
    public OrderController(OrderApplicationService orders) {
        this.orders = orders;
    }
    /**
     * @param command 创建输入
     * @return 实际订单标识
     */
    @RawResponse
    @PostMapping("/orders")
    public String create(@RequestBody CreateOrderCommand command) {
        return orders.create(command).id();
    }
    /**
     * @param command 明细输入
     * @return 实际订单标识
     */
    @RawResponse
    @PostMapping("/orders/lines")
    public String addLine(@RequestBody AddOrderLineCommand command) {
        return orders.addLine(command).id();
    }
    /**
     * @param id 订单标识
     * @param key 幂等键
     * @return 实际订单标识
     */
    @RawResponse
    @PostMapping("/orders/{id}/payment")
    public String pay(@PathVariable String id, @RequestParam String key) {
        return orders.pay(id, key).id();
    }
    /**
     * @param id 订单标识
     * @return 实际查询结果
     */
    @RawResponse
    @GetMapping("/orders/{id}")
    public OrderReadModel find(@PathVariable String id) {
        return orders.find(id);
    }
    /**
     * @param query 查询条件
     * @return 实际匹配结果
     */
    @RawResponse
    @PostMapping("/orders/query")
    public List<OrderReadModel> query(@RequestBody OrderQuery query) {
        return orders.query(query);
    }
    /**
     * @param id 订单标识
     * @return 已取消订单标识
     */
    @RawResponse
    @DeleteMapping("/orders/{id}")
    public String cancel(@PathVariable String id) {
        return orders.cancel(id).id();
    }
}
