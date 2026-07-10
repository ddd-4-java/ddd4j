package io.ddd4j.sample.quarkus.shiro.order.web;

import io.ddd4j.core.api.R;
import io.ddd4j.sample.quarkus.shiro.order.application.AddOrderLineCommand;
import io.ddd4j.sample.quarkus.shiro.order.application.CreateOrderCommand;
import io.ddd4j.sample.quarkus.shiro.order.application.OrderApplicationService;
import io.ddd4j.sample.quarkus.shiro.order.domain.model.Money;
import io.ddd4j.sample.quarkus.shiro.order.domain.model.Order;
import io.ddd4j.sample.quarkus.shiro.order.web.dto.AddOrderLineRequest;
import io.ddd4j.sample.quarkus.shiro.order.web.dto.CreateOrderRequest;
import io.ddd4j.sample.quarkus.shiro.order.web.dto.OrderResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * 订单 JAX-RS 资源（写侧）。
 *
 * <p>Quarkus 下使用 {@code @Path} / {@code @GET} / {@code @POST} 注解，
 * 通过 {@code @Inject} 注入 {@link OrderApplicationService}。
 *
 * <p>REST 端点：
 * <ul>
 *   <li>POST /orders                    创建订单</li>
 *   <li>GET  /orders                    列表查询</li>
 *   <li>GET  /orders/{id}              查询订单</li>
 *   <li>GET  /orders/by-order-no       按订单号查询</li>
 *   <li>POST /orders/{id}/lines        添加订单行</li>
 *   <li>POST /orders/{id}/pay          支付订单</li>
 *   <li>POST /orders/{id}/ship         发货订单</li>
 *   <li>POST /orders/{id}/cancel       取消订单</li>
 *   <li>GET  /orders/{id}/discount     预览折扣</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    private final OrderApplicationService orderApplicationService;

    @Inject
    public OrderResource(OrderApplicationService orderApplicationService) {
        this.orderApplicationService = orderApplicationService;
    }

    @POST
    public R<OrderResponse> create(CreateOrderRequest request) {
        Order order = orderApplicationService.createDraft(
                new CreateOrderCommand(request.orderNo(), request.buyerId(), request.buyerName())
        );
        return R.ok("order created", OrderResponse.from(order));
    }

    @GET
    @Path("/{id}")
    public R<OrderResponse> findById(@PathParam("id") String id) {
        Order order = orderApplicationService.findById(id);
        return R.ok(OrderResponse.from(order));
    }

    @GET
    @Path("/by-order-no")
    public R<OrderResponse> findByOrderNo(@QueryParam("orderNo") String orderNo) {
        Order order = orderApplicationService.findByOrderNo(orderNo);
        return R.ok(OrderResponse.from(order));
    }

    @GET
    public R<List<OrderResponse>> listAll() {
        List<OrderResponse> items = orderApplicationService.listAll().stream()
                .map(OrderResponse::from)
                .toList();
        return R.ok(items);
    }

    @POST
    @Path("/{id}/lines")
    public R<OrderResponse> addLine(@PathParam("id") String id, AddOrderLineRequest request) {
        Order order = orderApplicationService.addLine(new AddOrderLineCommand(
                id, request.goodsId(), request.goodsName(), request.quantity(), request.unitPrice()
        ));
        return R.ok("order line added", OrderResponse.from(order));
    }

    @POST
    @Path("/{id}/pay")
    public R<OrderResponse> pay(@PathParam("id") String id) {
        Order order = orderApplicationService.pay(id);
        return R.ok("order paid", OrderResponse.from(order));
    }

    @POST
    @Path("/{id}/ship")
    public R<OrderResponse> ship(@PathParam("id") String id) {
        Order order = orderApplicationService.ship(id);
        return R.ok("order shipped", OrderResponse.from(order));
    }

    @POST
    @Path("/{id}/cancel")
    public R<OrderResponse> cancel(@PathParam("id") String id) {
        Order order = orderApplicationService.cancel(id);
        return R.ok("order cancelled", OrderResponse.from(order));
    }

    @GET
    @Path("/{id}/discount")
    public R<DiscountView> previewDiscount(@PathParam("id") String id) {
        Money discounted = orderApplicationService.previewDiscount(id);
        return R.ok(new DiscountView(discounted.amount().toPlainString(), discounted.currency()));
    }

    /**
     * 折扣预览响应。
     */
    public record DiscountView(String amount, String currency) {
    }
}