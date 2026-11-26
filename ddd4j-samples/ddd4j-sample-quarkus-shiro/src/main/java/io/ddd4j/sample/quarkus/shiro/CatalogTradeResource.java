package io.ddd4j.sample.quarkus.shiro;

import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.adapter.http.CatalogResource;
import io.quarkus.runtime.Startup;
import javax.inject.Singleton;
import javax.inject.Inject;
import javax.ws.rs.Path;

@Startup
@javax.enterprise.context.ApplicationScoped
@Path("/orders/catalog")
public class CatalogTradeResource extends CatalogResource {
    @Inject
    void configure(CatalogApi catalog) {
        initialize(catalog);
    }
}
