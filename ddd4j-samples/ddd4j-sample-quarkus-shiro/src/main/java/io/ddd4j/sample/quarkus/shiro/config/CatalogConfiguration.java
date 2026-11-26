package io.ddd4j.sample.quarkus.shiro.config;

import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.start.CatalogRuntime;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class CatalogConfiguration {
    @Produces
    @ApplicationScoped
    CatalogApi catalog(@ConfigProperty(name = "catalog.jdbc.url") String url,
                       @ConfigProperty(name = "catalog.db.user") String username,
                       @ConfigProperty(name = "catalog.db.password") String password,
                       DomainEventPublisher publisher) {
        return CatalogRuntime.start(url, username, password, publisher);
    }
}
