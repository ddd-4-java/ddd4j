package io.ddd4j.sample.quarkus.cqrs.order.web;

import io.ddd4j.sample.quarkus.cqrs.order.application.AddOrderLineCommand;
import io.ddd4j.sample.quarkus.cqrs.order.application.CreateOrderCommand;
import io.ddd4j.sample.quarkus.cqrs.order.application.OrderApplicationService;
import io.ddd4j.sample.quarkus.cqrs.order.domain.model.Order;
import io.ddd4j.sample.quarkus.cqrs.order.domain.repository.OrderRepository;
import io.ddd4j.sample.quarkus.cqrs.order.domain.service.OrderDomainService;
import io.ddd4j.web.quarkus.TenantAwareResource;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 订单 JAX-RS 资源：演示 ddd4j-web-quarkus 的资源基类与 Quarkus REST 集成。
 *
 * <p>继承 {@link TenantAwareResource}，可直接调用 {@code getTenantId()} /
 * {@code getLang()} / {@code ok(data)} / {@code fail(msg)} 等辅助方法，
 * 同时复用 ddd4j 的统一响应包装 {@link io.ddd4j.core.api.R}。
 *
 * <h3>端点</h3>
 * <ul>
 *   <li>{@code GET    /orders/{id}}          - 查询订单</li>
 *   <li>{@code GET    /orders/orderNo/{no}}  - 按订单编号查询</li>
 *   <li>{@code POST   /orders}               - 创建草稿订单</li>
 *   <li>{@code POST   /orders/{id}/lines}    - 添加订单行</li>
 *   <li>{@code POST   /orders/{id}:pay}      - 支付订单</li>
 *   <li>{@code POST   /orders/{id}:ship}     - 发货订单</li>
 *   <li>{@code POST   /orders/{id}:cancel}   - 取消订单</li>
 *   <li>{@code POST   /orders/cancel-all}    - 批量取消买家草稿订单</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <p>Resource 层只做协议适配（HTTP ↔ 应用服务入参 / 响应），不包含业务规则。
 * 所有业务方法都委托给 {@link OrderApplicationService} 与 {@link OrderDomainService}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource extends TenantAwareResource {

    private final OrderApplicationService applicationService;
    private final OrderDomainService domainService;
    private final OrderRepository repository;

    @Inject
    public OrderResource(OrderApplicationService applicationService,
                         OrderDomainService domainService,
                         OrderRepository repository) {
        this.applicationService = applicationService;
        this.domainService = domainService;
        this.repository = repository;
    }

    /**
     * 按 ID 查询订单。
     *
     * @param id 订单 ID
     * @return 订单聚合（不存在返回 404）
     */
    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") String id) {
        Optional<Order> order = repository.findById(id);
        return order.map(this::ok)
                .orElseGet(() -> notFound("order not found: " + id));
    }

    /**
     * 按订单编号查询订单。
     */
    @GET
    @Path("/orderNo/{orderNo}")
    public Response getByOrderNo(@PathParam("orderNo") String orderNo) {
        return repository.findByOrderNo(orderNo)
                .map(this::ok)
                .orElseGet(() -> notFound("order not found by orderNo: " + orderNo));
    }

    /**
     * 创建草稿订单。
     */
    @POST
    public Response create(CreateOrderRequest request) {
        Order order = applicationService.createDraft(
                new CreateOrderCommand(request.orderNo(), request.buyerId(), request.buyerName()));
        return ok(order);
    }

    /**
     * 添加订单行。
     */
    @POST
    @Path("/{id}/lines")
    public Response addLine(@PathParam("id") String id, AddLineRequest request) {
        Order order = applicationService.addLine(
                new AddOrderLineCommand(id, request.goodsId(), request.goodsName(),
                        request.quantity(), request.unitPrice()));
        return ok(order);
    }

    /**
     * 支付订单。
     */
    @POST
    @Path("/{id}:pay")
    public Response pay(@PathParam("id") String id) {
        return ok(applicationService.pay(id));
    }

    /**
     * 发货订单。
     */
    @POST
    @Path("/{id}:ship")
    public Response ship(@PathParam("id") String id) {
        return ok(applicationService.ship(id));
    }

    /**
     * 取消订单。
     */
    @POST
    @Path("/{id}:cancel")
    public Response cancel(@PathParam("id") String id) {
        return ok(applicationService.cancel(id));
    }

    /**
     * 批量取消买家草稿订单（演示领域服务与 SPI）。
     */
    @POST
    @Path("/cancel-all")
    public Response cancelAllDrafts(CancelAllRequest request) {
        int cancelled = domainService.cancelAllDraftsOf(request.buyerId());
        return ok(cancelled);
    }

    // ========== 请求 DTO（record） ==========

    /**
     * 创建订单请求。
     */
    public record CreateOrderRequest(String orderNo, String buyerId, String buyerName) {
    }

    /**
     * 添加订单行请求。
     */
    public record AddLineRequest(String goodsId, String goodsName, int quantity, BigDecimal unitPrice) {
    }

    /**
     * 批量取消请求。
     */
    public record CancelAllRequest(String buyerId) {
    }
}