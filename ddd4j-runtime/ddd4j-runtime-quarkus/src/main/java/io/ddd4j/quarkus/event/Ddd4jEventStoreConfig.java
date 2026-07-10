package io.ddd4j.quarkus.event;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.fuin.esc.api.EventStore;
import org.fuin.esc.mem.InMemoryEventStore;

import java.util.concurrent.Executors;

/**
 * Quarkus EventStore 自动配置。
 * <p>
 * 根据 {@code ddd4j.ddd.event-store.type} 配置自动创建 {@link EventStore} 实例，
 * 默认使用内存版 EventStore（适用于开发/测试模式）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
@ApplicationScoped
public class Ddd4jEventStoreConfig {

    /**
     * EventStore 类型配置
     */
    @Inject
    @ConfigProperty(name = "ddd4j.ddd.event-store.type", defaultValue = "mem")
    String eventStoreType;

    @Produces
    @Singleton
    public EventStore eventStore() {
        log.info("Creating EventStore with type: {}", eventStoreType);

        if ("mem".equalsIgnoreCase(eventStoreType)) {
            log.info("Using in-memory EventStore (development/test mode)");
            return new InMemoryEventStore(Executors.newCachedThreadPool());
        }

        log.warn("EventStore type '{}' not supported by auto-configuration. Please provide your own EventStore bean.", eventStoreType);
        return new InMemoryEventStore(Executors.newCachedThreadPool());
    }

    void onStart(@Observes StartupEvent event) {
        log.info("Ddd4jEventStoreConfig initialized with type: {}", eventStoreType);
    }
}
