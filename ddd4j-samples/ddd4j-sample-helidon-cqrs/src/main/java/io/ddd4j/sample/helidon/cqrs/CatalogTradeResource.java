package io.ddd4j.sample.helidon.cqrs;

import com.google.common.collect.ImmutableList;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.sample.order.adapter.http.CatalogCqrs;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.adapter.http.CatalogCqrsResource;
import io.ddd4j.sample.order.start.CatalogRuntime;
import javax.ws.rs.Path;
import java.util.List;

@Path("/orders/catalog")
public class CatalogTradeResource extends CatalogCqrsResource {
    public CatalogTradeResource() {
        this(CatalogRuntime.start());
    }

    CatalogTradeResource(CatalogApi api) {
        super(new CatalogCqrs(api, new DefaultCommandBus(ImmutableList.of(CatalogCqrs.executor(api)))));
    }
}
