package io.ddd4j.sample.quarkus.cqrs.goods.web;

import io.ddd4j.sample.quarkus.cqrs.cache.GoodsCacheService;
import io.ddd4j.sample.quarkus.cqrs.goods.domain.GoodsRepository;
import io.ddd4j.web.quarkus.TenantAwareResource;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * 商品 CQRS 读侧资源 - 缓存优先版本（Quarkus）。
 *
 * <p>本资源是 CQRS 示例相对于基线 {@link GoodsQueryResource} 的增强：
 * <ul>
 *   <li>按 ID 查询走 {@link GoodsCacheService} 缓存</li>
 *   <li>列表查询同样走缓存</li>
 *   <li>按状态过滤也走缓存</li>
 *   <li>暴露 cache-stats 端点便于观察 CQRS 缓存命中率</li>
 * </ul>
 *
 * <p>REST 端点：
 * <ul>
 *   <li>GET /api/goods/query/by-id/{id}        按 ID（缓存优先）</li>
 *   <li>GET /api/goods/query/by-code/{code}    按编码（仓储直查）</li>
 *   <li>GET /api/goods/query/list              列表（缓存优先）</li>
 *   <li>GET /api/goods/query/by-status?status  按状态（缓存优先）</li>
 *   <li>GET /api/goods/query/count             计数</li>
 *   <li>GET /api/goods/query/cache-stats       缓存统计</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Path("/api/goods/query")
@Produces(MediaType.APPLICATION_JSON)
public class GoodsReadResource extends TenantAwareResource {

    private final GoodsCacheService goodsCacheService;
    private final GoodsRepository goodsRepository;

    @Inject
    public GoodsReadResource(GoodsCacheService goodsCacheService, GoodsRepository goodsRepository) {
        this.goodsCacheService = goodsCacheService;
        this.goodsRepository = goodsRepository;
    }

    /**
     * 按 ID 查询（缓存优先）。
     */
    @GET
    @Path("/by-id/{id}")
    public Response getById(@PathParam("id") Long id) {
        try {
            return ok(goodsCacheService.getById(id));
        } catch (IllegalArgumentException e) {
            return notFound(e.getMessage());
        }
    }

    /**
     * 按编码查询（仓储直查）。
     */
    @GET
    @Path("/by-code/{code}")
    public Response getByCode(@PathParam("code") String code) {
        return goodsRepository.findByCode(code)
                .map(this::ok)
                .orElseGet(() -> notFound("goods not found: " + code));
    }

    /**
     * 列表（缓存优先）。
     */
    @GET
    @Path("/list")
    public Response list() {
        return ok(goodsCacheService.listAll());
    }

    /**
     * 按状态过滤（缓存优先）。
     */
    @GET
    @Path("/by-status")
    public Response listByStatus(@QueryParam("status") String status) {
        return ok(goodsCacheService.listByStatus(status));
    }

    /**
     * 计数（基于缓存列表）。
     */
    @GET
    @Path("/count")
    public Response count() {
        return ok((long) goodsCacheService.listAll().size());
    }

    /**
     * 缓存统计（CQRS 监控）。
     */
    @GET
    @Path("/cache-stats")
    public Response cacheStats() {
        Map<String, Object> stats = goodsCacheService.stats();
        return ok(stats);
    }
}