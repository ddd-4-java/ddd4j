package io.ddd4j.sample.quarkus.order.web;

import io.ddd4j.sample.quarkus.order.cache.OrderCacheService;
import io.ddd4j.sample.quarkus.order.domain.model.Order;
import io.ddd4j.sample.quarkus.order.domain.model.OrderStatus;
import io.ddd4j.sample.quarkus.order.domain.repository.OrderRepository;
import io.ddd4j.sample.quarkus.order.infrastructure.InMemoryOrderRepository;
import io.ddd4j.web.quarkus.TenantAwareResource;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Objects;

/**
 * 订单 CQRS 查询端点：演示读写分离的查询侧设计。
 *
 * <p>与 {@link OrderResource}（命令端）形成 CQRS 对照：
 * <ul>
 *   <li>命令端（OrderResource）：处理创建/支付/发货/取消等写操作</li>
 *   <li>查询端（本类）：处理查询/列表/统计等读操作，走缓存优先</li>
 * </ul>
 *
 * <h3>端点</h3>
 * <ul>
 *   <li>{@code GET /query/orders/{id}}           - 按 ID 查询（缓存优先）</li>
 *   <li>{@code GET /query/orders}                 - 按条件查询列表</li>
 *   <li>{@code GET /query/orders/count}           - 统计订单数量</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <p>查询端不修改聚合状态，只读取数据。
 * 通过 {@link OrderCacheService} 实现缓存优先查询，
 * 展示 ddd4j-cache 在 Quarkus CDI 下的集成方式。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Path("/query/orders")
@Produces(MediaType.APPLICATION_JSON)
public class OrderQueryResource extends TenantAwareResource {

    private final OrderCacheService cacheService;
    private final OrderRepository repository;

    @Inject
    public OrderQueryResource(OrderCacheService cacheService, OrderRepository repository) {
        this.cacheService = cacheService;
        this.repository = repository;
    }

    /**
     * 按 ID 查询订单（缓存优先）。
     *
     * @param id 订单 ID
     * @return 订单聚合
     */
    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") String id) {
        return cacheService.getOrder(id)
                .map(this::ok)
                .orElseGet(() -> notFound("order not found: " + id));
    }

    /**
     * 按条件查询订单列表。
     *
     * <p>支持按状态过滤；不传 status 则返回全部。
     *
     * @param status 订单状态（可选）
     * @return 订单列表
     */
    @GET
    public Response list(@QueryParam("status") String status) {
        if (!(repository instanceof InMemoryOrderRepository inMemory)) {
            return ok(List.of());
        }
        List<Order> orders;
        if (status != null && !status.isBlank()) {
            OrderStatus orderStatus = OrderStatus.valueOf(status.toUpperCase());
            orders = inMemory.findByStatus(orderStatus);
        } else {
            orders = inMemory.findAll();
        }
        return ok(orders);
    }

    /**
     * 统计订单数量。
     *
     * @return 当前订单总数
     */
    @GET
    @Path("/count")
    public Response count() {
        if (repository instanceof InMemoryOrderRepository inMemory) {
            return ok(inMemory.count());
        }
        return ok(0L);
    }
}
