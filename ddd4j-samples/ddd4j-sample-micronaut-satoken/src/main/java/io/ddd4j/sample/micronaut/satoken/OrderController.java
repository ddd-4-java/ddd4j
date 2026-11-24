package io.ddd4j.sample.micronaut.satoken;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.domain.OrderQuery;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.QueryValue;
import java.util.List;
/** 原生认证过滤器保护的同步共享 Order HTTP 入口。 */
@Controller("/orders")
public class OrderController {
 private final OrderApplicationService orders;
 /** @param orders 共享应用服务 */
 public OrderController(OrderApplicationService orders) { this.orders = orders; }
 /**
 * @param command 创建参数
 * @return 订单标识
 */
 @Post public String create(@Body CreateOrderCommand command) { return orders.create(command).id(); }
 /**
 * @param command 添加明细参数
 * @return 订单标识
 */
 @Post("/lines") public String line(@Body AddOrderLineCommand command) { return orders.addLine(command).id(); }
 /**
 * @param id 订单标识
 * @param key 支付幂等键
 * @return 订单标识
 */
 @Post("/{id}/payment") public String pay(String id, @QueryValue String key) { return orders.pay(id, key).id(); }
 /**
 * @param id 订单标识
 * @return 取消后的订单标识
 */
 @Post("/{id}/cancel") public String cancel(String id) { return orders.cancel(id).id(); }
 /**
 * @param id 订单标识
 * @return 实际业务订单
 */
 @Get("/{id}") public OrderReadModel find(String id) { return orders.find(id); }
 /**
 * @param query 查询条件
 * @return 订单列表
 */
 @Post("/query") public List<OrderReadModel> query(@Body OrderQuery query) { return orders.query(query); }
}
