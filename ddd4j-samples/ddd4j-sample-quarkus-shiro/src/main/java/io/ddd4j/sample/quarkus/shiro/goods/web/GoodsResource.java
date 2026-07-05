package io.ddd4j.sample.quarkus.shiro.goods.web;

import io.ddd4j.core.api.R;
import io.ddd4j.sample.quarkus.shiro.goods.application.GoodsApplicationService;
import io.ddd4j.sample.quarkus.shiro.goods.domain.Goods;
import io.ddd4j.sample.quarkus.shiro.goods.domain.GoodsId;
import io.ddd4j.sample.quarkus.shiro.goods.domain.GoodsStatus;
import io.ddd4j.sample.quarkus.shiro.goods.web.dto.CreateGoodsRequest;
import io.ddd4j.sample.quarkus.shiro.goods.web.dto.UpdateGoodsRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * 商品 JAX-RS 资源（写侧）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Path("/api/goods")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GoodsResource {

    private final GoodsApplicationService applicationService;

    @Inject
    public GoodsResource(GoodsApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 创建商品。
     */
    @POST
    public R<Goods> create(CreateGoodsRequest request) {
        Goods goods = applicationService.create(
                request.code(), request.name(), request.price(), request.stock());
        return R.ok(goods);
    }

    /**
     * 更新商品。
     */
    @PUT
    @Path("/{id}")
    public R<Goods> update(@PathParam("id") Long id, UpdateGoodsRequest request) {
        Goods goods = applicationService.update(
                GoodsId.of(id), request.name(), request.price());
        return R.ok(goods);
    }

    /**
     * 调整商品状态。
     */
    @PUT
    @Path("/{id}/status")
    public R<Goods> changeStatus(@PathParam("id") Long id, @QueryParam("status") GoodsStatus status) {
        Goods goods = applicationService.changeStatus(GoodsId.of(id), status);
        return R.ok(goods);
    }

    /**
     * 删除商品（软删）。
     */
    @DELETE
    @Path("/{id}")
    public R<Void> delete(@PathParam("id") Long id) {
        applicationService.delete(GoodsId.of(id));
        return R.ok();
    }

    /**
     * 按 ID 查询商品。
     */
    @GET
    @Path("/{id}")
    public R<Goods> getById(@PathParam("id") Long id) {
        return R.ok(applicationService.getById(GoodsId.of(id)));
    }

    /**
     * 按编码查询商品。
     */
    @GET
    @Path("/by-code")
    public R<Goods> getByCode(@QueryParam("code") String code) {
        return R.ok(applicationService.getByCode(code));
    }
}