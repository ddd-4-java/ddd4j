package io.ddd4j.sample.dropwizard.cqrs;

import io.ddd4j.data.cqrs.dropwizard.DropwizardCommandBus;
import io.ddd4j.sample.order.adapter.http.CatalogCqrs;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.adapter.http.CatalogCqrsResource;
import io.dropwizard.core.Application;
import jakarta.ws.rs.Path;
import java.util.List;

@Path("/orders/catalog")
public class CatalogTradeResource extends CatalogCqrsResource {
    public CatalogTradeResource(Application<?> app, CatalogApi api) {
        super(new CatalogCqrs(api, DropwizardCommandBus.create(app, List.of(CatalogCqrs.executor(api)))));
    }
}
