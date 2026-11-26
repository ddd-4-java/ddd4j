package io.ddd4j.sample.order.adapter.http;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.Objects;

/** Jakarta REST 框架共用的商品建档、下单与查询入口。 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public abstract class CatalogResource {
    private CatalogEndpoints api;

    /**
     * 绑定商品交易 HTTP 路由。
     */
    protected CatalogResource() {
    }

    /**
     * 绑定商品交易 HTTP 路由。
     *
     * @param api 商品交易入口
     */
    protected CatalogResource(CatalogEndpoints api) {
        initialize(api);
    }

    /**
     * 初始化 JAX-RS 资源使用的业务入口。
     *
     * @param api 商品交易入口
     */
    protected void initialize(CatalogEndpoints api) {
        this.api = Objects.requireNonNull(api, "商品交易服务未装配");
    }

    /**
     * 执行当前框架的请求鉴权。
     *
     * @param method 待鉴权的业务操作
     */
    protected void authorize(String method) {
    }

    /**
     * 创建商品和初始规格。
     *
     * @param tenant 租户编号
     * @param code 租户内业务编号
     * @param body 请求 JSON 正文
     * @return 业务操作响应
     */
    @POST
    @Path("/goods/{code}")
    public Response createGoods(@HeaderParam("X-Tenant-Id") String tenant, @PathParam("code") String code, String body) {
        authorize("POST");
        Reply result = api.createGoods(tenant, code, body);
        return Response.status(result.getStatus()).type(MediaType.APPLICATION_JSON).entity(result.getBody()).build();
    }

    /**
     * 查询指定商品。
     *
     * @param tenant 租户编号
     * @param code 租户内业务编号
     * @return 业务操作响应
     */
    @GET
    @Path("/goods/{code}")
    public Response goods(@HeaderParam("X-Tenant-Id") String tenant, @PathParam("code") String code) {
        authorize("GET");
        Reply result = api.goods(tenant, code);
        return Response.status(result.getStatus()).type(MediaType.APPLICATION_JSON).entity(result.getBody()).build();
    }

    /**
     * 创建交易订单。
     *
     * @param tenant 租户编号
     * @param code 租户内业务编号
     * @param body 请求 JSON 正文
     * @return 业务操作响应
     */
    @POST
    @Path("/orders/{code}")
    public Response placeOrder(@HeaderParam("X-Tenant-Id") String tenant, @PathParam("code") String code, String body) {
        authorize("POST");
        Reply result = api.placeOrder(tenant, code, body);
        return Response.status(result.getStatus()).type(MediaType.APPLICATION_JSON).entity(result.getBody()).build();
    }

    /**
     * 确认订单支付。
     *
     * @param tenant 租户编号
     * @param orderNo 租户内订单编号
     * @param body 请求 JSON 正文
     * @return 业务操作响应
     */
    @POST
    @Path("/orders/{orderNo}/payment")
    public Response confirmPayment(@HeaderParam("X-Tenant-Id") String tenant,
            @PathParam("orderNo") String orderNo, String body) {
        authorize("POST");
        Reply reply = api.confirmPayment(tenant, orderNo, body);
        return Response.status(reply.getStatus()).type(MediaType.APPLICATION_JSON).entity(reply.getBody()).build();
    }

    /**
     * 取消指定订单。
     *
     * @param tenant 租户编号
     * @param orderNo 租户内订单编号
     * @return 业务操作响应
     */
    @POST
    @Path("/orders/{orderNo}/cancel")
    public Response cancelOrder(@HeaderParam("X-Tenant-Id") String tenant, @PathParam("orderNo") String orderNo) {
        authorize("CANCEL");
        Reply reply = api.cancelOrder(tenant, orderNo);
        return Response.status(reply.getStatus()).type(MediaType.APPLICATION_JSON).entity(reply.getBody()).build();
    }

    /**
     * 完成指定订单的履约。
     *
     * @param tenant 租户编号
     * @param orderNo 租户内订单编号
     * @return 业务操作响应
     */
    @POST
    @Path("/orders/{orderNo}/fulfill")
    public Response fulfillOrder(@HeaderParam("X-Tenant-Id") String tenant, @PathParam("orderNo") String orderNo) {
        authorize("POST");
        Reply reply = api.fulfillOrder(tenant, orderNo);
        return Response.status(reply.getStatus()).type(MediaType.APPLICATION_JSON).entity(reply.getBody()).build();
    }

    /**
     * 查询指定订单。
     *
     * @param tenant 租户编号
     * @param code 租户内业务编号
     * @return 业务操作响应
     */
    @GET
    @Path("/orders/{code}")
    public Response order(@HeaderParam("X-Tenant-Id") String tenant, @PathParam("code") String code) {
        authorize("GET");
        Reply result = api.order(tenant, code);
        return Response.status(result.getStatus()).type(MediaType.APPLICATION_JSON).entity(result.getBody()).build();
    }
}
