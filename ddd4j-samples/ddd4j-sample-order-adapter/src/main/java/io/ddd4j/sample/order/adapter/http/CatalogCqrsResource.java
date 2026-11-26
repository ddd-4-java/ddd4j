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

    protected CatalogCqrsResource() {
    }

    protected CatalogCqrsResource(CatalogCqrs catalog) {
        initializeProjection(catalog);
    }

    protected void initializeProjection(CatalogCqrs catalog) {
        initialize(catalog);
        this.catalog = catalog;
    }

    @POST
    @Path("/projections/orders/{code}")
    public Response project(@HeaderParam("X-Tenant-Id") String tenant, @PathParam("code") String code) {
        Reply reply = catalog.project(tenant, code);
        return Response.status(reply.getStatus()).type(MediaType.APPLICATION_JSON).entity(reply.getBody()).build();
    }
}
