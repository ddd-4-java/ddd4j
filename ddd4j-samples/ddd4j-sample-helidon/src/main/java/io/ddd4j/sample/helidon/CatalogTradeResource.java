package io.ddd4j.sample.helidon;

import io.ddd4j.sample.order.adapter.http.CatalogResource;
import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import jakarta.ws.rs.Path;

@Path("/api")
public class CatalogTradeResource extends CatalogResource {
    public CatalogTradeResource(CatalogEndpoints catalog) {
        super(catalog);
    }
}
