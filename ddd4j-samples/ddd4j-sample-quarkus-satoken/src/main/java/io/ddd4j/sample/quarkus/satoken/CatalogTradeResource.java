package io.ddd4j.sample.quarkus.satoken;

import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.adapter.http.CatalogResource;
import io.quarkus.runtime.Startup;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import jakarta.ws.rs.Path;

/** Quarkus \u5546\u54c1\u4ea4\u6613 HTTP \u5165\u53e3\uff0c\u8f6c\u53d1\u5546\u54c1\u548c\u8ba2\u5355\u8bf7\u6c42\u5230\u5171\u7528\u7684\u4ea4\u6613\u7528\u4f8b\u3002 */
@Startup
@Singleton
@Path("/orders/catalog")
public class CatalogTradeResource extends CatalogResource {
    /**
     * \u5c06\u4f20\u5165\u7684\u5546\u54c1\u4ea4\u6613\u64cd\u4f5c\u7ed1\u5b9a\u5230 HTTP \u5165\u53e3\u3002
     * @param catalog \u5df2\u88c5\u914d\u7684\u5546\u54c1\u4ea4\u6613 HTTP \u64cd\u4f5c
     */
    @Inject
    public CatalogTradeResource(CatalogApi catalog) {
        super(catalog);
    }
}
