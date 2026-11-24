package io.ddd4j.sample.dropwizard.satoken;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.web.core.error.WebStatusException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.function.Supplier;
import com.fasterxml.jackson.databind.ObjectMapper;
/** HTTP/权限适配；实际订单规则复用共享服务。 */
@Path("/") @Produces("application/json") @Consumes("application/json")
public final class AuthOrderResource {
 private final OrderApplicationService orders; private final Supplier<NativeAuth> auth;
 private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
 /**
 * @param orders 共享业务
 * @param auth 当前 Managed 原生认证 */
 public AuthOrderResource(OrderApplicationService orders,Supplier<NativeAuth> auth){this.orders=orders;this.auth=auth;}
 private void require(String permission){if(!SubjectKit.getSubject().isPermitted(permission)){throw new WebStatusException(403,"permission denied");}}
 /** @return 健康状态 */
 @GET @Path("health") public Map<String,String> health(){return Map.of("status","UP");}
 /**
 * @param request 原生凭证
 * @return 原生令牌 */
 @POST @Path("auth/login") public Map<String,String> login(Map<String,String> request){return Map.of("token",auth.get().login(request.get("username"),request.get("password")));}
 /** @return 退出结果 */
 @POST @Path("auth/logout") public Map<String,Boolean> logout(){SubjectKit.getSubject().logout();return Map.of("success",true);}
 /**
 * @param request 创建参数
 * @return 已创建订单 */
 @POST @Path("orders") public Response create(Map<String,Object> request){require("order:write");var order=orders.create(mapper.convertValue(request,CreateOrderCommand.class));return Response.status(201).entity(orders.find(order.id())).build();}
 /**
 * @param id 订单 ID
 * @return 查询结果 */
 @GET @Path("orders/{id}") public OrderReadModel find(@PathParam("id") String id){require("order:read");return orders.find(id);}
 /**
 * @param id 订单 ID
 * @param request 明细参数
 * @return 更新结果 */
 @POST @Path("orders/{id}/lines") public OrderReadModel line(@PathParam("id") String id,Map<String,Object> request){require("order:write");var values=new java.util.HashMap<>(request);values.put("orderId",id);orders.addLine(mapper.convertValue(values,AddOrderLineCommand.class));return orders.find(id);}
 /**
 * @param id 订单 ID
 * @param key 支付幂等键
 * @return 支付结果 */
 @POST @Path("orders/{id}/pay") public OrderReadModel pay(@PathParam("id") String id,@HeaderParam("Idempotency-Key") String key){require("order:write");orders.pay(id,key);return orders.find(id);}
}
