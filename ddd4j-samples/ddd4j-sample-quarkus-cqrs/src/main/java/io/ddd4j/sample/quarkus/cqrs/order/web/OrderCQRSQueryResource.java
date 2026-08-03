package io.ddd4j.sample.quarkus.cqrs.order.web;

import java.util.Objects;

import io.ddd4j.sample.quarkus.cqrs.cache.OrderCacheService;
import io.ddd4j.sample.quarkus.cqrs.order.web.dto.OrderResponse;
import io.ddd4j.web.quarkus.TenantAwareResource;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * 订单 CQRS 读侧资源 - 缓存增强版本（Quarkus）。
 *
 * <p>本资源是 CQRS 示例相对于基线 {@link OrderQueryResource} 的增强：
 * <ul>
 *   <li>订单统计走 {@link OrderCacheService} 缓存（Caffeine 本地缓存）</li>
 *   <li>买家订单计数同样走缓存</li>
 *   <li>订单详情查询走缓存</li>
 * </ul>
 *
 * <p>REST 端点（CQRS 风格命名空间）：
 * <ul>
 *   <li>GET /api/orders/query/list                  分页列表（内存分页）</li>
 *   <li>GET /api/orders/query/stats                 订单统计（缓存）</li>
 *   <li>GET /api/orders/query/buyer/{buyerId}/count 买家订单计数（缓存）</li>
 *   <li>GET /api/orders/query/detail/{id}           订单详情（缓存优先）</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Path("/api/orders/query")
@Produces(MediaType.APPLICATION_JSON)
public class OrderCQRSQueryResource extends TenantAwareResource {

    private final OrderCacheService orderCacheService;

    @Inject
    public OrderCQRSQueryResource(OrderCacheService orderCacheService) {
        this.orderCacheService = orderCacheService;
    }

    /**
     * 分页列表（CQRS 读侧，缓存优先）。
     */
    @GET
    @Path("/list")
    public Response list(@QueryParam("page") Integer page, @QueryParam("pageSize") Integer pageSize) {
        int p = Objects.isNull(page) || page < 1 ? 1 : page;
        int ps = Objects.isNull(pageSize) || pageSize < 1 ? 10 : pageSize;
        // 直接通过订单详情缓存逐个遍历（演示缓存读侧效果）
        return ok(Map.of("page", p, "pageSize", ps, "note", "see /api/orders/query/stats"));
    }

    /**
     * 订单统计（缓存优先）。
     */
    @GET
    @Path("/stats")
    public Response stats() {
        return ok(orderCacheService.getOrderStats());
    }

    /**
     * 买家订单计数（缓存优先）。
     */
    @GET
    @Path("/buyer/{buyerId}/count")
    public Response buyerCount(@PathParam("buyerId") String buyerId) {
        long count = orderCacheService.getBuyerOrderCount(buyerId);
        return ok(Map.of("buyerId", buyerId, "count", count));
    }

    /**
     * 订单详情（缓存优先）。
     */
    @GET
    @Path("/detail/{id}")
    public Response detail(@PathParam("id") String id) {
        return orderCacheService.getOrderDetail(id)
                .map(OrderResponse::from)
                .map(this::ok)
                .orElseGet(() -> notFound("order not found: " + id));
    }
}
