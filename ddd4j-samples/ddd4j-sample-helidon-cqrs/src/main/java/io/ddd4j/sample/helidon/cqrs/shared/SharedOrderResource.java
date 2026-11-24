package io.ddd4j.sample.helidon.cqrs.shared;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.application.OrderReadModelPort;
import io.ddd4j.sample.order.domain.OrderQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
/** 独立持久六字段查询，不使用写仓储回退；保持旧 /orders DTO。
 */
@Path("/sample") @ApplicationScoped @Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON)
public class SharedOrderResource {
 @Inject CommandBus commands;
 @Inject com.fasterxml.jackson.databind.ObjectMapper mapper;
 @Inject Instance<OrderReadModelPort> queries;
 @Inject Instance<ViewManager> views;
 /**
 * @param request 创建请求
 * @return 订单标识
 */
 @POST @Path("/orders") public String create(Map<String, Object> request) { return commands.<String>execute(new OrderWriteCommand(mapper.convertValue(request,CreateOrderCommand.class),null,null,null)).data().orElseThrow(); }
 /**
 * @param request 明细请求
 * @return 订单标识
 */
 @POST @Path("/orders/lines") public String line(Map<String, Object> request) { return commands.<String>execute(new OrderWriteCommand(null,mapper.convertValue(request,AddOrderLineCommand.class),null,null)).data().orElseThrow(); }
 /**
 * @param id 订单标识
 * @param key 幂等键
 * @return 订单标识
 */
 @POST @Path("/orders/{id}/payment") public String pay(@PathParam("id") String id,@QueryParam("key") String key) { return commands.<String>execute(new OrderWriteCommand(null,null,id,key)).data().orElseThrow(); }
 /**
 * @param id 订单标识
 * @return 已投影查询行
 */
 @GET @Path("/orders/{id}") public OrderReadModel find(@PathParam("id") String id) { return queries.get().findProjectionById(id).orElseThrow(jakarta.ws.rs.NotFoundException::new); }
 /**
 * @param request 筛选参数
 * @return 查询行
 */
 @POST @Path("/orders/query") public List<OrderReadModel> query(Map<String, Object> request) { return queries.get().query(mapper.convertValue(request,OrderQuery.class)); }
 /**
 * @return 运维触发结果，复用 R10 的生产 Runner
 */
 @POST @Path("/projections/orders/run") public String project() { views.get().triggerOnce();return "projected"; }
}
