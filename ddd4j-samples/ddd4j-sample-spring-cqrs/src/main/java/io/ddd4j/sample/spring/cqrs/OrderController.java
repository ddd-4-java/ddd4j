package io.ddd4j.sample.spring.cqrs;

import io.ddd4j.annotation.api.RawResponse;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.application.OrderReadModelPort;
import io.ddd4j.sample.order.domain.OrderQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** 命令使用实际运行时总线，查询只读取独立投影仓储。 */
@RestController
public class OrderController {
    private final CommandBus commands;
    private final OrderReadModelPort queries;
    private final OrderProjection projection;
    /**
     * @param commands 唯一实际命令总线
     * @param queries 查询仓储
     * @param projection 持久投影
     */
    public OrderController(CommandBus commands, OrderReadModelPort queries, OrderProjection projection) {
        this.commands = commands; this.queries = queries; this.projection = projection;
    }
    /**
     * @param input 创建输入
     * @return 实际订单标识
     */
    @RawResponse @PostMapping("/orders")
    public String create(@RequestBody CreateOrderCommand input) {
        return commands.<String>execute(new OrderWriteCommand(input, null, null, null)).data().orElseThrow();
    }
    /**
     * @param input 明细输入
     * @return 实际订单标识
     */
    @RawResponse @PostMapping("/orders/lines")
    public String addLine(@RequestBody AddOrderLineCommand input) {
        return commands.<String>execute(new OrderWriteCommand(null, input, null, null)).data().orElseThrow();
    }
    /**
     * @param id 订单标识
     * @param key 幂等键
     * @return 实际订单标识
     */
    @RawResponse @PostMapping("/orders/{id}/payment")
    public String pay(@PathVariable String id, @RequestParam String key) {
        return commands.<String>execute(new OrderWriteCommand(null, null, id, key)).data().orElseThrow();
    }
    /**
     * @param id 订单标识
     * @return 已投影数据，尚未投影时失败
     */
    @RawResponse @GetMapping("/orders/{id}")
    public OrderReadModel find(@PathVariable String id) { return queries.findProjectionById(id).orElseThrow(); }
    /**
     * @param input 查询条件
     * @return 独立查询结果
     */
    @RawResponse @PostMapping("/orders/query")
    public List<OrderReadModel> query(@RequestBody OrderQuery input) { return queries.query(input); }
    /**
     * @return 本次持久化源位置；本地样例的显式运维入口
     */
    @RawResponse @PostMapping("/projections/orders/run")
    public long project() { projection.project(); return projection.position(); }
}
