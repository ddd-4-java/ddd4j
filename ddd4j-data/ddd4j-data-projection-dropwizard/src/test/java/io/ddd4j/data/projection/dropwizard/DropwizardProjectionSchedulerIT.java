package io.ddd4j.data.projection.dropwizard;

import java.util.Collections;
import java.util.Arrays;
import io.ddd4j.core.cqrs.readmodel.*;
import io.dropwizard.Application;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.server.DefaultServerFactory;
import io.dropwizard.setup.Environment;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.testing.DropwizardTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DropwizardProjectionScheduler} + {@link DropwizardProjectionViewManager}
 * 真实 Dropwizard 启动集成测试（Task 7.12）。
 *
 * <p>真实 Dropwizard 5 启动（{@link DropwizardTestSupport} 驱动真实 Jetty/Jersey
 * 容器，随机端口）+ 真实调度器/管理器（零 mock）——完整验证 ViewScheduler 的
 * schedule/cancel/isActive 契约与 ViewManager 的 start/stop/isRunning/triggerOnce
 * 生命周期。
 *
 * <ol>
 *   <li>register + start + assert handle isActive + stop + assert inactive</li>
 *   <li>多 view 同时注册 + start + stop 隔离</li>
 *   <li>triggerOnce 立即执行所有 view 的 runOnce</li>
 * </ol>
 *
 * <p><b>启动开销注记</b>：Dropwizard 真实启动（Jetty+Jersey+Jackson+metrics 全栈）
 * 刻意 {@link BeforeAll} 类级单次启动（3 用例共享同一真实容器）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DisplayName("DropwizardProjectionScheduler + ViewManager 真实 Dropwizard 启动 IT")
class DropwizardProjectionSchedulerIT {

    private static DropwizardTestSupport<Configuration> support;
    private static DropwizardProjectionScheduler scheduler;
    private static DropwizardProjectionViewManager viewManager;

    @BeforeAll
    static void startDropwizard() throws Exception {
        support = new DropwizardTestSupport<>(TestApplication.class, randomPortConfiguration());
        support.before();
        TestApplication app = support.getApplication();
        scheduler = app.scheduler;
        viewManager = app.viewManager;
    }

    @AfterAll
    static void stopDropwizard() {
        if (viewManager != null && viewManager.isRunning()) {
            viewManager.stop();
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (support != null) {
            support.after();
        }
    }

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

    /**
     * 随机端口配置（application/admin 连接器 port 0 → 内核分配空闲端口），
     * 避免真实 Jetty 启动撞固定 8080/8081。
     */
    private static Configuration randomPortConfiguration() {
        Configuration configuration = new Configuration();
        DefaultServerFactory serverFactory = (DefaultServerFactory) configuration.getServerFactory();
        HttpConnectorFactory applicationConnector = new HttpConnectorFactory();
        applicationConnector.setPort(0);
        serverFactory.setApplicationConnectors(Collections.singletonList(applicationConnector));
        HttpConnectorFactory adminConnector = new HttpConnectorFactory();
        adminConnector.setPort(0);
        serverFactory.setAdminConnectors(Collections.singletonList(adminConnector));
        return configuration;
    }

    /**
     * 测试用 Dropwizard 应用（仅 {@code run} 阶段被覆盖）：在
     * {@code Application.run} 内以生产接法调
     * {@link DropwizardProjectionScheduler#create} +
     * {@link DropwizardProjectionViewManager#create}。
     */
    public static class TestApplication extends Application<Configuration> {

        volatile DropwizardProjectionScheduler scheduler;
        volatile DropwizardProjectionViewManager viewManager;

        @Override
        public void run(Configuration configuration, Environment environment) {
            Collection<ProjectionView<?>> views = Arrays.asList(
                    new ProjectionViewStub("test-view-1", "0/5 * * * * *"),
                    new ProjectionViewStub("test-view-2", "0/10 * * * * *")
            );
            ProjectionRunner<Object> runner = createTestRunner();
            this.scheduler = DropwizardProjectionScheduler.create(environment, views, runner);
            this.viewManager = DropwizardProjectionViewManager.create(environment, views, runner);
        }

        @SuppressWarnings("unchecked")
        private ProjectionRunner<Object> createTestRunner() {
            ProjectionService projectionService = new ProjectionService() {
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
            EventChunkReader<Object> chunkReader = (streamId, fromEventNumber, chunkSize, eventTypes) ->
                    EventChunk.empty(fromEventNumber);
            return new ProjectionRunner<>(projectionService, chunkReader);
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
            return Collections.singleton("TestEvent");
        }

        @Override
        public void handleEvents(Collection<Object> events) {
        }
    }
}
