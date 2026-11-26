package io.ddd4j.sample.quarkus.satoken;

import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.adapter.http.CatalogResource;
import io.quarkus.runtime.Startup;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import jakarta.ws.rs.Path;

@Startup
@Singleton
@Path("/orders/catalog")
public class CatalogTradeResource extends CatalogResource {
    @Inject
    public CatalogTradeResource(CatalogApi catalog) {
        super(catalog);
    }
}
