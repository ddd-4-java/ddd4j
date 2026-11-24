package io.ddd4j.sample.quarkus.cqrs.shared;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.domain.OrderQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
/** 六字段共享持久读模型入口；旧完整明细 DTO 仍属于原内存演示路径。 */
@Path("/sample") @Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON) @ApplicationScoped
public class SharedOrderResource {
    @Inject CommandBus commands;
    @Inject SharedOrderStorage storage;
    @Inject OrderOutboxRelay relay;
    @Inject ViewManager views;
    /**
     * @param command 创建输入
     * @return 订单标识
     */
    @POST @Path("/orders") public String create(CreateOrderCommand command) {
        return commands.<String>execute(new OrderWriteCommand(command, null, null, null)).data().orElseThrow();
    }
    /**
     * @param command 明细输入
     * @return 订单标识
     */
    @POST @Path("/orders/lines") public String line(AddOrderLineCommand command) {
        return commands.<String>execute(new OrderWriteCommand(null, command, null, null)).data().orElseThrow();
    }
    /**
     * @param id 订单标识
     * @param key 支付幂等键
     * @return 订单标识
     */
    @POST @Path("/orders/{id}/payment") public String pay(@PathParam("id") String id, @QueryParam("key") String key) {
        return commands.<String>execute(new OrderWriteCommand(null, null, id, key)).data().orElseThrow();
    }
    /**
     * @param id 订单标识
     * @return 独立查询行，不回退写仓储
     */
    @GET @Path("/orders/{id}") public OrderReadModel find(@PathParam("id") String id) {
        return storage.queries().findProjectionById(id).orElseThrow(NotFoundException::new);
    }
    /**
     * @param query 查询条件
     * @return 独立查询行
     */
    @POST @Path("/orders/query") public List<OrderReadModel> query(OrderQuery query) { return storage.queries().query(query); }
    /** @return 显式运维触发结果；与后台调度使用同一个 Runner */
    @POST @Path("/projections/orders/run") public String project() { relay.relay(); views.triggerOnce(); return "projected"; }
}
