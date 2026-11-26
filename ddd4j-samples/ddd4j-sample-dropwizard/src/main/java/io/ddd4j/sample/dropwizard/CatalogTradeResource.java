package io.ddd4j.sample.dropwizard;

import io.ddd4j.sample.order.adapter.http.CatalogResource;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.start.CatalogRuntime;
import jakarta.ws.rs.Path;

@Path("/api")
public class CatalogTradeResource extends CatalogResource {
    public CatalogTradeResource() {
        this(CatalogRuntime.start());
    }

    public CatalogTradeResource(CatalogApi api) {
        super(api);
    }
}
