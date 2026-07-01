package io.ddd4j.quarkus.ddd.config;

import io.ddd4j.core.ddd.config.DddProperties;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.fuin.esc.api.EventStore;
import org.fuin.esc.mem.InMemoryEventStore;

import java.util.concurrent.Executors;

/**
 * Quarkus EventStore 自动配置。
 */
@Slf4j
@ApplicationScoped
public class Ddd4jEventStoreConfig {

    @Inject
    DddProperties dddProperties;

    @Produces
    @Singleton
    public EventStore eventStore() {
        String type = dddProperties.getEventStore().getType();
        log.info("Creating EventStore with type: {}", type);

        if ("mem".equals(type)) {
            log.info("Using in-memory EventStore (development/test mode)");
            return new InMemoryEventStore(Executors.newCachedThreadPool());
        }

        log.warn("EventStore type '{}' not supported by auto-configuration. Please provide your own EventStore bean.", type);
        return new InMemoryEventStore(Executors.newCachedThreadPool());
    }

    void onStart(@Observes StartupEvent event) {
        log.info("Ddd4jEventStoreConfig initialized with type: {}", dddProperties.getEventStore().getType());
    }
}
