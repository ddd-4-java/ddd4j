package io.ddd4j.sample.order.adapter.http;

import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * JAX-RS 的商品交易 CQRS 入口。
 */
public abstract class CatalogCqrsResource extends CatalogResource {
    /**
     * CQRS 示例的 HTTP 操作入口。
     */
    private final CatalogCqrs catalog;

    /**
     * 绑定交易与投影的 HTTP 路由。
     *
     * @param catalog 商品交易 CQRS 入口
     */
    protected CatalogCqrsResource(CatalogCqrs catalog) {
        super(catalog);
        this.catalog = catalog;
    }

    /**
     * 按已提交的订单快照重建读模型。
     *
     * @param tenant 租户编号
     * @param code   租户内业务编号
     * @return 业务操作响应
     */
    @POST
    @Path("/projections/orders/{code}")
    public Response project(@HeaderParam("X-Tenant-Id") String tenant, @PathParam("code") String code) {
        Reply reply = catalog.project(tenant, code);
        return Response.status(reply.getStatus()).type(MediaType.APPLICATION_JSON).entity(reply.getBody()).build();
    }
}
