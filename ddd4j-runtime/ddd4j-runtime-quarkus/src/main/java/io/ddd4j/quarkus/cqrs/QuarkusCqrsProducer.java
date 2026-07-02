package io.ddd4j.quarkus.cqrs;

import io.ddd4j.core.cqrs.projection.*;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * Quarkus CQRS 默认 CDI 生产者。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@ApplicationScoped
public class QuarkusCqrsProducer {

    @Produces
    @DefaultBean
    @Singleton
    public ProjectionPositionRepository projectionPositionRepository() {
        return new InMemoryProjectionPositionRepository();
    }

    @Produces
    @DefaultBean
    @Singleton
    public ProjectionService projectionService(ProjectionPositionRepository repository) {
        return new DefaultProjectionService(repository);
    }

    @Produces
    @DefaultBean
    @Singleton
    public ProjectionRunner<Object> projectionRunner(
            ProjectionService projectionService,
            Instance<EventChunkReader<Object>> chunkReaders) {
        EventChunkReader<Object> chunkReader = chunkReaders.isResolvable()
                ? chunkReaders.get()
                : new NoopEventChunkReader<>();
        return new ProjectionRunner<>(projectionService, chunkReader);
    }
}
