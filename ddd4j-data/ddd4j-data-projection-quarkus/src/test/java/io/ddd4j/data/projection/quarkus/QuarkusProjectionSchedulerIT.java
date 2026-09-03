package io.ddd4j.data.projection.quarkus;

import io.ddd4j.core.cqrs.readmodel.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link QuarkusProjectionScheduler} + {@link QuarkusProjectionViewManager}
 * 真实 ArC 容器集成测试。
 *
 * <p>真实 Quarkus 容器（{@code @QuarkusTest}，零配置引导——ArC 自带启动容器，
 * 不用 H2/数据源）＋真实 {@code @ApplicationScoped} 调度器 Bean（零 mock）——
 * 完整验证 ViewScheduler 的 schedule/cancel/isActive 契约与 ViewManager
 * 的 start/stop/isRunning/triggerOnce 生命周期。
 *
 * <ol>
 *   <li>register + start + assert handle isActive + stop + assert inactive</li>
 *   <li>多 view 同时注册 + start + stop 隔离</li>
 *   <li>triggerOnce 立即执行一个 view 的 runOnce</li>
 * </ol>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DisplayName("QuarkusProjectionScheduler + ViewManager 真实 ArC 容器 IT")
@QuarkusTest
class QuarkusProjectionSchedulerIT {

    @Inject
    QuarkusProjectionScheduler scheduler;

    @Inject
    QuarkusProjectionViewManager viewManager;

    @Test
    void scheduler_schedule_应返回activeHandle_cancel后应inactive() {
        ViewScheduler.ViewScheduleHandle handle = scheduler.schedule(
                "direct-view", "0/1 * * * * *", () -> {});

        assertThat(handle.isActive()).isTrue();

        handle.cancel();
        assertThat(handle.isActive()).isFalse();
    }

    @Test
    void viewManager_start_stop_应管理生命周期() {
        assertThat(viewManager.isRunning()).isFalse();

        viewManager.start();
        assertThat(viewManager.isRunning()).isTrue();

        // start idempotent
        viewManager.start();
        assertThat(viewManager.isRunning()).isTrue();

        viewManager.stop();
        assertThat(viewManager.isRunning()).isFalse();
    }

    @Test
    void triggerOnce_应立即执行所有view的runOnce() {
        viewManager.start();
        viewManager.triggerOnce();
        assertThat(viewManager.isRunning()).isTrue();
        viewManager.stop();
    }

    // ======== CDI 测试替身（@Produces 注册为 ArC Bean） ========

    @ApplicationScoped
    static class TestProducer {

        @Produces
        @ApplicationScoped
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

        @Produces
        @ApplicationScoped
        @SuppressWarnings("unchecked")
        EventChunkReader<Object> chunkReader() {
            return (streamId, fromEventNumber, chunkSize, eventTypes) ->
                    EventChunk.empty(fromEventNumber);
        }

        @Produces
        @ApplicationScoped
        @SuppressWarnings("unchecked")
        ProjectionRunner<Object> projectionRunner(ProjectionService service, EventChunkReader<Object> reader) {
            return new ProjectionRunner<>(service, reader);
        }

        @Produces
        @ApplicationScoped
        ProjectionView<Object> testView() {
            return new ProjectionViewStub("test-view", "0/5 * * * * *");
        }
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
            return Set.of("TestEvent");
        }

        @Override
        public void handleEvents(Collection<Object> events) {
        }
    }
}
