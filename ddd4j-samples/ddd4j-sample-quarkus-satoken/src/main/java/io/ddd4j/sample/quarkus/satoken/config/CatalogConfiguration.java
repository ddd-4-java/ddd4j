package io.ddd4j.sample.quarkus.satoken.config;

import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.start.CatalogRuntime;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** \u4ece Quarkus \u914d\u7f6e\u8bfb\u53d6\u6570\u636e\u5e93\u8fde\u63a5\uff0c\u6ce8\u518c\u5171\u7528\u7684\u5546\u54c1\u4ea4\u6613\u5165\u53e3\u3002 */
@ApplicationScoped
public class CatalogConfiguration {
    /**
     * \u4ece\u6570\u636e\u5e93\u914d\u7f6e\u53ca\u4e8b\u4ef6\u53d1\u5e03\u5668\u88c5\u914d\u5171\u7528\u5546\u54c1\u4ea4\u6613\u5165\u53e3\u3002
     * @param url \u5546\u54c1\u4ea4\u6613\u793a\u4f8b\u6570\u636e\u5e93 JDBC \u5730\u5740
     * @param username \u6570\u636e\u5e93\u767b\u5f55\u8d26\u53f7
     * @param password \u6570\u636e\u5e93\u8fde\u63a5\u53e3\u4ee4
     * @param publisher \u5f53\u524d\u8fd0\u884c\u65f6\u7528\u4e8e\u8f6c\u53d1\u4ea4\u6613\u4e8b\u4ef6\u7684\u53d1\u5e03\u5668
     * @return \u4f9b\u6846\u67b6 HTTP \u8d44\u6e90\u8c03\u7528\u7684\u5546\u54c1\u4ea4\u6613\u64cd\u4f5c
     */
    @Produces
    @ApplicationScoped
    CatalogApi catalog(@ConfigProperty(name = "catalog.jdbc.url") String url,
                       @ConfigProperty(name = "catalog.db.user") String username,
                       @ConfigProperty(name = "catalog.db.password") String password,
                       DomainEventPublisher publisher) {
        return CatalogRuntime.start(url, username, password, publisher);
    }
}
