package io.ddd4j.sample.quarkus.order.web;

import io.ddd4j.core.api.R;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.domain.Money;
import io.ddd4j.sample.order.domain.Order;
import io.ddd4j.sample.order.domain.OrderQuery;
import io.ddd4j.sample.order.domain.OrderStatus;
import io.ddd4j.web.core.context.WebHeaders;
import io.ddd4j.web.core.error.WebStatusException;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** JAX-RS translation layer for the shared Order application. */
@Path("/api/orders")
@Produces(MediaType.APPLICATION_JSON)
public class OrderResource {

    private final OrderApplicationService applicationService;

    @Inject
    public OrderResource(OrderApplicationService applicationService) {
        this.applicationService = Objects.requireNonNull(applicationService,
                "applicationService must not be null");
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(CreateOrderRequest request) {
        Order order = applicationService.create(new CreateOrderCommand(
                request.orderNo(), request.buyerId(), request.buyerName()));
        return Response.status(Response.Status.CREATED).entity(R.ok(toResponse(order))).build();
    }

    @GET
    @Path("/by-no")
    public R<OrderReadModel> findByOrderNo(@QueryParam("orderNo") String orderNo) {
        return R.ok(applicationService.findByOrderNo(orderNo));
    }

    @GET
    public R<List<OrderReadModel>> query(@QueryParam("buyerId") String buyerId,
                                         @QueryParam("status") String status,
                                         @QueryParam("page") Integer page,
                                         @QueryParam("size") Integer size) {
        OrderStatus orderStatus = StrKit.isBlank(status)
                ? null : OrderStatus.valueOf(status.toUpperCase(Locale.ROOT));
        return R.ok(applicationService.query(new OrderQuery(buyerId, orderStatus,
                Objects.isNull(page) ? 1 : page, Objects.isNull(size) ? 20 : size)));
    }

    @GET
    @Path("/{id}")
    public R<OrderReadModel> find(@PathParam("id") String id) {
        return R.ok(applicationService.find(id));
    }

    @POST
    @Path("/{id}/lines")
    @Consumes(MediaType.APPLICATION_JSON)
    public R<OrderResponse> addLine(@PathParam("id") String id, AddOrderLineRequest request) {
        Order order = applicationService.addLine(new AddOrderLineCommand(id,
                request.goodsId(), request.goodsName(), request.quantity(), request.unitPrice()));
        return R.ok(toResponse(order));
    }

    @POST
    @Path("/{id}/pay")
    public R<OrderResponse> pay(@PathParam("id") String id,
                                @HeaderParam(WebHeaders.IDEMPOTENCY_KEY) String idempotencyKey) {
        if (StrKit.isBlank(idempotencyKey)) {
            throw new WebStatusException(400, "Idempotency-Key is required");
        }
        return R.ok(toResponse(applicationService.pay(id, idempotencyKey)));
    }

    @POST
    @Path("/{id}/ship")
    public R<OrderResponse> ship(@PathParam("id") String id) {
        return R.ok(toResponse(applicationService.ship(id)));
    }

    @POST
    @Path("/{id}/cancel")
    public R<OrderResponse> cancel(@PathParam("id") String id) {
        return R.ok(toResponse(applicationService.cancel(id)));
    }

    private static OrderResponse toResponse(Order order) {
        Money total = order.totalAmount();
        return new OrderResponse(order.id(), order.orderNo(), order.buyerId(), order.buyerName(),
                order.status(), total.amount(), total.currency(), order.lines().size());
    }

    public record CreateOrderRequest(String orderNo, String buyerId, String buyerName) {
    }

    public record AddOrderLineRequest(String goodsId, String goodsName, int quantity, BigDecimal unitPrice) {
    }

    public record OrderResponse(String id, String orderNo, String buyerId, String buyerName,
                                OrderStatus status, BigDecimal totalAmount, String currency, int lineCount) {
    }
}
