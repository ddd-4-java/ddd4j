package io.ddd4j.sample.dropwizard;

import io.ddd4j.sample.order.adapter.http.CatalogResource;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.start.CatalogRuntime;
import jakarta.ws.rs.Path;

/** Dropwizard \u5546\u54c1\u4ea4\u6613 HTTP \u5165\u53e3\uff0c\u8f6c\u53d1\u5546\u54c1\u548c\u8ba2\u5355\u8bf7\u6c42\u5230\u5171\u7528\u7684\u4ea4\u6613\u7528\u4f8b\u3002 */
@Path("/api")
public class CatalogTradeResource extends CatalogResource {
    /** \u4f7f\u7528\u793a\u4f8b\u7684\u9ed8\u8ba4\u8fd0\u884c\u914d\u7f6e\u7ec4\u88c5\u5546\u54c1\u4ea4\u6613\u8d44\u6e90\u3002 */
    public CatalogTradeResource() {
        this(CatalogRuntime.start());
    }

    /**
     * \u5c06\u4f20\u5165\u7684\u5546\u54c1\u4ea4\u6613\u64cd\u4f5c\u7ed1\u5b9a\u5230 HTTP \u5165\u53e3\u3002
     * @param api \u5171\u7528\u7684\u5546\u54c1\u4ea4\u6613 HTTP \u64cd\u4f5c
     */
    public CatalogTradeResource(CatalogApi api) {
        super(api);
    }
}
