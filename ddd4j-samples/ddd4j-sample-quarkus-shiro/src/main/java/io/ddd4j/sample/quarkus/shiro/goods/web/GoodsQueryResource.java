package io.ddd4j.sample.quarkus.shiro.goods.web;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.api.R;
import io.ddd4j.sample.quarkus.shiro.goods.application.GoodsApplicationService;
import io.ddd4j.sample.quarkus.shiro.goods.domain.Goods;
import io.ddd4j.sample.quarkus.shiro.goods.domain.GoodsQuery;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品充血查询资源（读侧）。
 *
 * <p>接收查询参数并构造 {@link GoodsQuery}，调用充血查询方法。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Path("/api/goods")
@Produces(MediaType.APPLICATION_JSON)
public class GoodsQueryResource {

    private final GoodsApplicationService applicationService;

    @Inject
    public GoodsQueryResource(GoodsApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 充血分页查询。
     */
    @GET
    @Path("/page")
    public R<Page<Goods>> page(
            @QueryParam("current") Long current,
            @QueryParam("size") Long size,
            @QueryParam("code") String code,
            @QueryParam("nameLike") String nameLike,
            @QueryParam("status") String status,
            @QueryParam("priceMin") BigDecimal priceMin,
            @QueryParam("priceMax") BigDecimal priceMax,
            @QueryParam("orderBys") String orderBys) {
        return R.ok(applicationService.pageQuery(buildQuery(current, size, code, nameLike, status, priceMin, priceMax, orderBys)));
    }

    /**
     * 充血列表查询。
     */
    @GET
    @Path("/list")
    public R<List<Goods>> list(
            @QueryParam("code") String code,
            @QueryParam("nameLike") String nameLike,
            @QueryParam("status") String status,
            @QueryParam("priceMin") BigDecimal priceMin,
            @QueryParam("priceMax") BigDecimal priceMax,
            @QueryParam("orderBys") String orderBys) {
        return R.ok(applicationService.listQuery(buildQuery(1L, 0L, code, nameLike, status, priceMin, priceMax, orderBys)));
    }

    /**
     * 充血计数。
     */
    @GET
    @Path("/count")
    public R<Long> count(
            @QueryParam("code") String code,
            @QueryParam("nameLike") String nameLike,
            @QueryParam("status") String status,
            @QueryParam("priceMin") BigDecimal priceMin,
            @QueryParam("priceMax") BigDecimal priceMax) {
        return R.ok(applicationService.countQuery(buildQuery(1L, 0L, code, nameLike, status, priceMin, priceMax, null)));
    }

    private GoodsQuery buildQuery(Long current, Long size, String code, String nameLike, String status,
                                    BigDecimal priceMin, BigDecimal priceMax, String orderBys) {
        GoodsQuery query = new GoodsQuery();
        if (current != null) {
            query.setCurrent(current);
        }
        if (size != null) {
            query.setSize(size);
        }
        query.setCode(code);
        query.setNameLike(nameLike);
        query.setStatus(parseStatus(status));
        query.setPriceMin(priceMin);
        query.setPriceMax(priceMax);
        query.setOrderBys(orderBys);
        return query;
    }

    private io.ddd4j.sample.quarkus.shiro.goods.domain.GoodsStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return io.ddd4j.sample.quarkus.shiro.goods.domain.GoodsStatus.valueOf(status.toUpperCase());
    }
}