package io.ddd4j.data.projection.micronaut;

import java.util.Collections;
import io.ddd4j.core.cqrs.readmodel.*;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Set;

/**
 * 集成测试专用装配工厂：{@code @MicronautTest} 引导的真实 BeanContext 需要
 * {@link ProjectionRunner}、{@link ProjectionView} 等 Bean 定义。
 * 本工厂以 {@code @Factory} 工厂方法注册测试替身（等价于 Quarkus 的
 * {@code @Produces @ApplicationScoped}）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Factory
class TestFactory {

    @Singleton
    ProjectionService projectionService() {
        return new ProjectionService() {
            @Override
            public void resetProjectionPosition(String streamId) {
            }

            @Override
            public long readProjectionPosition(String streamId) {
                return 0;
            }

            @Override
            public ProjectionPosition updateProjectionPosition(String streamId, long nextEventNumber) {
                return null;
            }
        };
    }

    @Singleton
    @SuppressWarnings("unchecked")
    EventChunkReader<Object> chunkReader() {
        return (streamId, fromEventNumber, chunkSize, eventTypes) ->
                EventChunk.empty(fromEventNumber);
    }

    @Singleton
    @SuppressWarnings("unchecked")
    ProjectionRunner<Object> projectionRunner(ProjectionService service, EventChunkReader<Object> reader) {
        return new ProjectionRunner<>(service, reader);
    }

    @Singleton
    ProjectionView<Object> testView() {
        return new ProjectionViewStub("test-view", "0/5 * * * * *");
    }

    private static class ProjectionViewStub implements ProjectionView<Object> {

        private final String name;
        private final String cron;

        ProjectionViewStub(String name, String cron) {
            this.name = name;
            this.cron = cron;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getCron() {
            return cron;
        }

        @Override
        public Collection<String> getEventTypes() {
            return Collections.singleton("TestEvent");
        }

        @Override
        public void handleEvents(Collection<Object> events) {
        }
    }
}
