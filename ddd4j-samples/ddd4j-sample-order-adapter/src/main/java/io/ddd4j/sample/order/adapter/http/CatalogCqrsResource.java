package io.ddd4j.sample.order.adapter.http;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/** JAX-RS 的商品交易 CQRS 入口。 */
public abstract class CatalogCqrsResource extends CatalogResource {
    private CatalogCqrs catalog;

    /**
     * 绑定交易与投影的 HTTP 路由。
     */
    protected CatalogCqrsResource() {
    }

    /**
     * 绑定交易与投影的 HTTP 路由。
     *
     * @param catalog 商品交易 CQRS 入口
     */
    protected CatalogCqrsResource(CatalogCqrs catalog) {
        initializeProjection(catalog);
    }

    /**
     * 初始化 CQRS 资源使用的业务入口。
     *
     * @param catalog 商品交易 CQRS 入口
     */
    protected void initializeProjection(CatalogCqrs catalog) {
        initialize(catalog);
        this.catalog = catalog;
    }

    /**
     * 按已提交的订单快照重建读模型。
     *
     * @param tenant 租户编号
     * @param code 租户内业务编号
     * @return 业务操作响应
     */
    @POST
    @Path("/projections/orders/{code}")
    public Response project(@HeaderParam("X-Tenant-Id") String tenant, @PathParam("code") String code) {
        Reply reply = catalog.project(tenant, code);
        return Response.status(reply.getStatus()).type(MediaType.APPLICATION_JSON).entity(reply.getBody()).build();
    }
}
