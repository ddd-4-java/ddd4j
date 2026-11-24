package io.ddd4j.sample.dropwizard.cqrs.persistent;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.sample.order.application.OrderReadModelPort;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.web.core.error.WebStatusException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.Response;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
/** 新持久六字段查询入口，不改旧 /orders DTO，不从写仓储回退。 */
@Path("/sample") @Produces("application/json") @Consumes("application/json")
public final class PersistentOrderResource {
 private final CommandBus commands;private final OrderReadModelPort queries;private final Runnable project;
 private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
 /**
 * @param commands 原生命令总线
 * @param queries 独立查询端口
 * @param project 生产 Runner */
 public PersistentOrderResource(CommandBus commands,OrderReadModelPort queries,Runnable project){this.commands=commands;this.queries=queries;this.project=project;}
 private Map<String,String> write(OrderWriteCommand command){var result=commands.<String>execute(command);if(!result.isSuccess()){throw new WebStatusException(409,result.getMessage());}return Map.of("orderId",result.getData());}
 /**
 * @param body 创建输入
 * @return 订单标识 */
 @POST @Path("orders") public Response create(Map<String,Object> body){return Response.status(201).entity(write(new OrderWriteCommand(mapper.convertValue(body,CreateOrderCommand.class),null,null,null))).build();}
 /**
 * @param id 订单标识
 * @return 只查询已投影的持久模型 */
 @GET @Path("orders/{id}") public Object find(@PathParam("id") String id){return queries.findProjectionById(id).orElseThrow(()->new WebStatusException(404,"projection not found"));}
 /**
 * @param id 订单 ID
 * @param body 明细输入
 * @return 订单标识 */
 @POST @Path("orders/{id}/lines") public Object line(@PathParam("id") String id,Map<String,Object> body){var values=new java.util.HashMap<>(body);values.put("orderId",id);return write(new OrderWriteCommand(null,mapper.convertValue(values,AddOrderLineCommand.class),null,null));}
 /**
 * @param id 订单 ID
 * @param key 幂等键
 * @return 订单标识 */
 @POST @Path("orders/{id}/pay") public Object pay(@PathParam("id") String id,@HeaderParam("Idempotency-Key") String key){return write(new OrderWriteCommand(null,null,id,key));}
 /** @return 运维触发结果，不属于调试专用路由 */
 @POST @Path("projections/orders/run") public Object project(){project.run();return Map.of("success",true);}
}
