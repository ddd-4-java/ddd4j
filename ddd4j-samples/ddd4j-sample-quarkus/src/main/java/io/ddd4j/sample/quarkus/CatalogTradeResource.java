package io.ddd4j.sample.quarkus;

import io.ddd4j.sample.order.adapter.http.CatalogResource;
import io.ddd4j.sample.order.start.CatalogRuntime;
import jakarta.ws.rs.Path;
import jakarta.inject.Inject;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@io.quarkus.runtime.Startup
@jakarta.inject.Singleton
@io.quarkus.arc.properties.IfBuildProperty(name = "sample.goods.standalone", stringValue = "false", enableIfMissing = true)
@Path("/api")
public class CatalogTradeResource extends CatalogResource {
    @Inject
    public CatalogTradeResource(@ConfigProperty(name = "catalog.jdbc.url") String url,
                                @ConfigProperty(name = "catalog.db.user") String username,
                                @ConfigProperty(name = "catalog.db.password") String password,
                                DomainEventPublisher publisher) {
        super(CatalogRuntime.start(url, username, password, publisher));
    }
}
