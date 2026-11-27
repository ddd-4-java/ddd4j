package io.ddd4j.sample.quarkus;

import io.ddd4j.sample.order.adapter.http.CatalogResource;
import io.ddd4j.sample.order.start.CatalogRuntime;
import jakarta.ws.rs.Path;
import jakarta.inject.Inject;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Quarkus \u5546\u54c1\u4ea4\u6613 HTTP \u5165\u53e3\uff0c\u8f6c\u53d1\u5546\u54c1\u548c\u8ba2\u5355\u8bf7\u6c42\u5230\u5171\u7528\u7684\u4ea4\u6613\u7528\u4f8b\u3002 */
@io.quarkus.runtime.Startup
@jakarta.inject.Singleton
@io.quarkus.arc.properties.IfBuildProperty(name = "sample.goods.standalone", stringValue = "false", enableIfMissing = true)
@Path("/api")
public class CatalogTradeResource extends CatalogResource {
    /**
     * \u6309\u8fd0\u884c\u6846\u67b6\u63d0\u4f9b\u7684\u8fde\u63a5\u914d\u7f6e\u521d\u59cb\u5316\u5546\u54c1\u4ea4\u6613 HTTP \u5165\u53e3\u3002
     * @param url \u5546\u54c1\u4ea4\u6613\u6570\u636e\u5e93\u7684 JDBC \u5730\u5740
     * @param username \u6570\u636e\u5e93\u8d26\u53f7
     * @param password \u6570\u636e\u5e93\u8d26\u53f7\u5bc6\u7801
     * @param publisher \u5546\u54c1\u4ea4\u6613\u9886\u57df\u4e8b\u4ef6\u53d1\u5e03\u5668
     */
    @Inject
    public CatalogTradeResource(@ConfigProperty(name = "catalog.jdbc.url") String url,
                                @ConfigProperty(name = "catalog.db.user") String username,
                                @ConfigProperty(name = "catalog.db.password") String password,
                                DomainEventPublisher publisher) {
        super(CatalogRuntime.start(url, username, password, publisher));
    }
}
