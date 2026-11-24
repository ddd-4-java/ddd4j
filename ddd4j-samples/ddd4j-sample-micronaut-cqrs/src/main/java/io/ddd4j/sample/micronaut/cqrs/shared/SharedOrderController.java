package io.ddd4j.sample.micronaut.cqrs.shared;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.application.OrderReadModelPort;
import io.ddd4j.sample.order.domain.OrderQuery;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.context.annotation.Requires;
import java.util.List;
/** 独立六字段持久读模型；不调用应用服务的写仓储回退查询。 */
@Controller("/sample") @ExecuteOn(TaskExecutors.BLOCKING)
@Requires(property="ddd4j.cqrs.projection.enabled", value="true")
public class SharedOrderController {
 private final CommandBus commands;
 private final OrderReadModelPort queries;
 private final ViewManager views;
 /**
 * @param commands R09 原生命令总线
 * @param queries 独立读仓储
 * @param views R09 投影管理器
 */
 public SharedOrderController(CommandBus commands,OrderReadModelPort queries,ViewManager views) {
  this.commands=commands;this.queries=queries;this.views=views;
 }
 /**
 * @param command 创建参数
 * @return 订单标识
 */
 @Post("/orders") public String create(@Body CreateOrderCommand command) { return commands.<String>execute(new OrderWriteCommand(command,null,null,null)).data().orElseThrow(); }
 /**
 * @param command 添加明细参数
 * @return 订单标识
 */
 @Post("/orders/lines") public String line(@Body AddOrderLineCommand command) { return commands.<String>execute(new OrderWriteCommand(null,command,null,null)).data().orElseThrow(); }
 /**
 * @param id 订单标识
 * @param key 幂等键
 * @return 订单标识
 */
 @Post("/orders/{id}/payment") public String pay(String id,@QueryValue String key) { return commands.<String>execute(new OrderWriteCommand(null,null,id,key)).data().orElseThrow(); }
 /**
 * @param id 订单标识
 * @return 独立投影，尚未投影返回404
 */
 @Get("/orders/{id}") public OrderReadModel find(String id) { return queries.findProjectionById(id).orElseThrow(() -> new io.micronaut.http.exceptions.HttpStatusException(io.micronaut.http.HttpStatus.NOT_FOUND,"not projected")); }
 /**
 * @param query 筛选参数
 * @return 独立查询行
 */
 @Post("/orders/query") public List<OrderReadModel> query(@Body OrderQuery query) { return queries.query(query); }
 /** @return 运维触发结果；与自动调度使用同一 Runner */
 @Post("/projections/orders/run") public String project() { views.triggerOnce(); return "projected"; }
}
