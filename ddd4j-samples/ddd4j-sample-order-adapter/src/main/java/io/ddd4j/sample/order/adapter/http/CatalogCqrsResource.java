package io.ddd4j.sample.order.adapter.http;

import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** JAX-RS 的商品交易 CQRS 入口。 */
public abstract class CatalogCqrsResource extends CatalogResource {
    private final CatalogCqrs catalog;

    protected CatalogCqrsResource(CatalogCqrs catalog) {
        super(catalog);
        this.catalog = catalog;
    }

    @POST
    @Path("/projections/orders/{code}")
    public Response project(@HeaderParam("X-Tenant-Id") String tenant, @PathParam("code") String code) {
        Reply reply = catalog.project(tenant, code);
        return Response.status(reply.getStatus()).type(MediaType.APPLICATION_JSON).entity(reply.getBody()).build();
    }
}
