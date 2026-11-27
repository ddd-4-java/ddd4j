package io.ddd4j.sample.quarkus.shiro;

import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.adapter.http.CatalogResource;
import io.quarkus.runtime.Startup;
import javax.inject.Singleton;
import javax.inject.Inject;
import javax.ws.rs.Path;

/** Quarkus \u5546\u54c1\u4ea4\u6613 HTTP \u5165\u53e3\uff0c\u8f6c\u53d1\u5546\u54c1\u548c\u8ba2\u5355\u8bf7\u6c42\u5230\u5171\u7528\u7684\u4ea4\u6613\u7528\u4f8b\u3002 */
@Startup
@javax.enterprise.context.ApplicationScoped
@Path("/orders/catalog")
public class CatalogTradeResource extends CatalogResource {
    /**
     * 执行 <code>configure</code> 对应的处理。
     *
     * @param catalog 传入此操作的 <code>CatalogApi</code> 类型 <code>catalog</code>
     */
    @Inject
    void configure(CatalogApi catalog) {
        initialize(catalog);
    }
}
