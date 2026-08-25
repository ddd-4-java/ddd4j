package io.ddd4j.sample.helidon.cqrs.web;

import io.ddd4j.sample.helidon.cqrs.command.CreateOrderCommand;
import io.ddd4j.sample.helidon.cqrs.cqrs.CommandBus;
import io.ddd4j.sample.helidon.cqrs.cqrs.ViewManager;
import io.ddd4j.sample.helidon.cqrs.readmodel.OrderSummaryView;
import io.ddd4j.sample.helidon.cqrs.readmodel.OrderSummaryViewEntity;
import io.ddd4j.sample.helidon.cqrs.repository.EventSourcingOrderRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.Objects;

/**
 * 订单 REST 资源（Helidon MP 运行时）。
 *
 * <p>通过 CDI {@code @Inject} 获取 CQRS 组件，
 * 不再直接访问 {@code HelidonCqrsApplication} 静态字段。
 */
@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class OrderResource {

    private final EventSourcingOrderRepository orderRepository;
    private final CommandBus commandBus;
    private final OrderSummaryView readView;
    private final ViewManager viewManager;

    @Inject
    public OrderResource(EventSourcingOrderRepository orderRepository,
                         CommandBus commandBus,
                         OrderSummaryView readView,
                         ViewManager viewManager) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
        this.commandBus = Objects.requireNonNull(commandBus, "commandBus must not be null");
        this.readView = Objects.requireNonNull(readView, "readView must not be null");
        this.viewManager = Objects.requireNonNull(viewManager, "viewManager must not be null");
    }

    @POST
    public Response createOrder(CreateOrderRequest request) {
        if (orderRepository.findByOrderNo(request.orderNo()).isPresent()) {
            return Response.status(409)
                    .entity(Map.of("success", false, "message", "order already exists: " + request.orderNo()))
                    .build();
        }

        CreateOrderCommand command = new CreateOrderCommand(
                request.orderNo(), request.buyerId(), request.buyerName());
        String orderId = commandBus.execute(command);

        return Response.status(201)
                .entity(Map.of("success", true, "orderId", orderId))
                .build();
    }

    @GET
    @Path("/{id}")
    public Response getOrder(@PathParam("id") String id) {
        OrderSummaryViewEntity entity = readView.findById(id);
        if (entity == null) {
            return Response.status(404).build();
        }
        return Response.ok(entity).build();
    }

    public record CreateOrderRequest(String orderNo, String buyerId, String buyerName) {
    }
}
