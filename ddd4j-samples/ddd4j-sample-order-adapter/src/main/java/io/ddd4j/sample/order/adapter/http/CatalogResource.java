package io.ddd4j.sample.order.adapter.http;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Jakarta REST 框架共用的商品建档、下单与查询入口。 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public abstract class CatalogResource {
    private final CatalogEndpoints api;

    protected CatalogResource(CatalogEndpoints api) {
        this.api = api;
    }

    protected void authorize(String method) {
    }

    @POST
    @Path("/goods/{code}")
    public Response createGoods(@HeaderParam("X-Tenant-Id") String tenant, @PathParam("code") String code, String body) {
        authorize("POST");
        Reply result = api.createGoods(tenant, code, body);
        return Response.status(result.getStatus()).type(MediaType.APPLICATION_JSON).entity(result.getBody()).build();
    }

    @GET
    @Path("/goods/{code}")
    public Response goods(@HeaderParam("X-Tenant-Id") String tenant, @PathParam("code") String code) {
        authorize("GET");
        Reply result = api.goods(tenant, code);
        return Response.status(result.getStatus()).type(MediaType.APPLICATION_JSON).entity(result.getBody()).build();
    }

    @POST
    @Path("/orders/{code}")
    public Response placeOrder(@HeaderParam("X-Tenant-Id") String tenant, @PathParam("code") String code, String body) {
        authorize("POST");
        Reply result = api.placeOrder(tenant, code, body);
        return Response.status(result.getStatus()).type(MediaType.APPLICATION_JSON).entity(result.getBody()).build();
    }

    @POST
    @Path("/orders/{orderNo}/payment")
    public Response confirmPayment(@HeaderParam("X-Tenant-Id") String tenant,
            @PathParam("orderNo") String orderNo, String body) {
        authorize("POST");
        Reply reply = api.confirmPayment(tenant, orderNo, body);
        return Response.status(reply.getStatus()).type(MediaType.APPLICATION_JSON).entity(reply.getBody()).build();
    }

    @POST
    @Path("/orders/{orderNo}/cancel")
    public Response cancelOrder(@HeaderParam("X-Tenant-Id") String tenant, @PathParam("orderNo") String orderNo) {
        authorize("CANCEL");
        Reply reply = api.cancelOrder(tenant, orderNo);
        return Response.status(reply.getStatus()).type(MediaType.APPLICATION_JSON).entity(reply.getBody()).build();
    }

    @POST
    @Path("/orders/{orderNo}/fulfill")
    public Response fulfillOrder(@HeaderParam("X-Tenant-Id") String tenant, @PathParam("orderNo") String orderNo) {
        authorize("POST");
        Reply reply = api.fulfillOrder(tenant, orderNo);
        return Response.status(reply.getStatus()).type(MediaType.APPLICATION_JSON).entity(reply.getBody()).build();
    }

    @GET
    @Path("/orders/{code}")
    public Response order(@HeaderParam("X-Tenant-Id") String tenant, @PathParam("code") String code) {
        authorize("GET");
        Reply result = api.order(tenant, code);
        return Response.status(result.getStatus()).type(MediaType.APPLICATION_JSON).entity(result.getBody()).build();
    }
}
