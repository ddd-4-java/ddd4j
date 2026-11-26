package io.ddd4j.sample.dropwizard.cqrs;

import io.ddd4j.dropwizard.command.DropwizardCommandBus;
import io.ddd4j.sample.order.adapter.http.CatalogCqrs;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.adapter.http.CatalogCqrsResource;
import io.ddd4j.sample.order.start.CatalogRuntime;
import jakarta.ws.rs.Path;
import java.util.List;

@Path("/orders/catalog")
public class CatalogTradeResource extends CatalogCqrsResource {
    public CatalogTradeResource() {
        this(CatalogRuntime.start());
    }

    public CatalogTradeResource(CatalogApi api) {
        super(new CatalogCqrs(api, new DropwizardCommandBus(List.of(CatalogCqrs.executor(api)))));
    }
}
