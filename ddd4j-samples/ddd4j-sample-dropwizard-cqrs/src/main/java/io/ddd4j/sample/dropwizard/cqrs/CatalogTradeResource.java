package io.ddd4j.sample.dropwizard.cqrs;

import io.ddd4j.dropwizard.command.DropwizardCommandBus;
import io.ddd4j.sample.order.adapter.http.CatalogCqrs;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.adapter.http.CatalogCqrsResource;
import io.ddd4j.sample.order.start.CatalogRuntime;
import jakarta.ws.rs.Path;
import java.util.List;

/** Dropwizard \u5546\u54c1\u4ea4\u6613 HTTP \u5165\u53e3\uff0c\u63a5\u5165\u793a\u4f8b\u7684\u547d\u4ee4\u603b\u7ebf\u53ca\u8ba2\u5355\u6295\u5f71\u3002 */
@Path("/orders/catalog")
public class CatalogTradeResource extends CatalogCqrsResource {
    /** \u4f7f\u7528\u793a\u4f8b\u7684\u9ed8\u8ba4\u8fd0\u884c\u914d\u7f6e\u7ec4\u88c5\u5546\u54c1\u4ea4\u6613\u8d44\u6e90\u3002 */
    public CatalogTradeResource() {
        this(CatalogRuntime.start());
    }

    /**
     * \u5c06\u4f20\u5165\u7684\u5546\u54c1\u4ea4\u6613\u64cd\u4f5c\u7ed1\u5b9a\u5230 HTTP \u5165\u53e3\u3002
     * @param api \u5171\u7528\u7684\u5546\u54c1\u4ea4\u6613 HTTP \u64cd\u4f5c
     */
    public CatalogTradeResource(CatalogApi api) {
        super(new CatalogCqrs(api, new DropwizardCommandBus(List.of(CatalogCqrs.executor(api)))));
    }
}
