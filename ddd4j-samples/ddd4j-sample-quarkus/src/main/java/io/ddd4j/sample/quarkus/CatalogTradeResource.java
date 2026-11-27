package io.ddd4j.sample.quarkus;

import io.ddd4j.sample.order.adapter.http.CatalogResource;
import io.ddd4j.sample.order.start.CatalogRuntime;
import javax.ws.rs.Path;
import javax.inject.Inject;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Quarkus \u5546\u54c1\u4ea4\u6613 HTTP \u5165\u53e3\uff0c\u8f6c\u53d1\u5546\u54c1\u548c\u8ba2\u5355\u8bf7\u6c42\u5230\u5171\u7528\u7684\u4ea4\u6613\u7528\u4f8b\u3002 */
@io.quarkus.runtime.Startup
@javax.enterprise.context.ApplicationScoped
@io.quarkus.arc.properties.IfBuildProperty(name = "sample.goods.standalone", stringValue = "false", enableIfMissing = true)
@Path("/api")
public class CatalogTradeResource extends CatalogResource {
    /**
     * 执行 <code>configure</code>，调用 <code>CatalogRuntime.start</code>。
     *
     * @param url 传入此操作的 <code>String</code> 类型 <code>url</code>
     * @param username 传入此操作的 <code>String</code> 类型 <code>username</code>
     * @param password 传入此操作的 <code>String</code> 类型 <code>password</code>
     * @param publisher 传入此操作的 <code>DomainEventPublisher</code> 类型 <code>publisher</code>
     */
    @Inject
    void configure(@ConfigProperty(name = "catalog.jdbc.url") String url,
                                @ConfigProperty(name = "catalog.db.user") String username,
                                @ConfigProperty(name = "catalog.db.password") String password,
                                DomainEventPublisher publisher) {
        initialize(CatalogRuntime.start(url, username, password, publisher));
    }
}
