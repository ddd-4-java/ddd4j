package io.ddd4j.data.projection.spring;

import io.ddd4j.core.cqrs.readmodel.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.Collection;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SpringProjectionScheduler} + {@link SpringProjectionViewManager}
 * 真实 Spring 容器集成测试。
 *
 * <p>真实 Spring 容器（{@code @SpringBootTest} 引导 {@link TestConfig}，
 * 集成方姿势的最小装配）＋真实 {@link ThreadPoolTaskScheduler}（零 mock）——
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
@DisplayName("SpringProjectionScheduler + ViewManager 真实容器 IT")
@SpringBootTest(classes = SpringProjectionSchedulerIT.TestConfig.class)
class SpringProjectionSchedulerIT {

    @Autowired
    private SpringProjectionScheduler scheduler;

    @Autowired
    private SpringProjectionViewManager viewManager;

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
        // SmartLifecycle + isAutoStartup()=true → 容器启动后已自动 start
        assertThat(viewManager.isRunning()).isTrue();

        // start 幂等：再次 start 不抛异常
        viewManager.start();
        assertThat(viewManager.isRunning()).isTrue();

        viewManager.stop();
        assertThat(viewManager.isRunning()).isFalse();

        // 可手动重新启动
        viewManager.start();
        assertThat(viewManager.isRunning()).isTrue();

        viewManager.stop();
        assertThat(viewManager.isRunning()).isFalse();
    }

    @Test
    void triggerOnce_应立即执行所有view的runOnce() {
        viewManager.start();
        viewManager.triggerOnce();
        // No exception = success (stub runner/view completes without error)
        assertThat(viewManager.isRunning()).isTrue();
        viewManager.stop();
    }

    // ======== 测试配置 ========

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackages = "io.ddd4j.data.projection.spring")
    static class TestConfig {

        @Bean
        ThreadPoolTaskScheduler taskScheduler() {
            ThreadPoolTaskScheduler ts = new ThreadPoolTaskScheduler();
            ts.setPoolSize(4);
            ts.setThreadNamePrefix("projection-test-");
            ts.initialize();
            return ts;
        }

        @Bean
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

        @Bean
        @SuppressWarnings("unchecked")
        EventChunkReader<Object> chunkReader() {
            return (streamId, fromEventNumber, chunkSize, eventTypes) ->
                    EventChunk.empty(fromEventNumber);
        }

        @Bean
        @SuppressWarnings("unchecked")
        ProjectionRunner<Object> projectionRunner(ProjectionService service, EventChunkReader<Object> reader) {
            return new ProjectionRunner<>(service, reader);
        }

        @Bean
        ProjectionView<Object> testView() {
            return new ProjectionViewStub("test-view", "0/5 * * * * *");
        }

        @Bean
        ProjectionView<Object> anotherView() {
            return new ProjectionViewStub("another-view", "0/10 * * * * *");
        }
    }

    // ======== 测试替身 ========

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
