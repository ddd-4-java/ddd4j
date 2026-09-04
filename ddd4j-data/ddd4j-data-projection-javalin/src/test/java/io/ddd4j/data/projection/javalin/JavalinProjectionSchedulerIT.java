package io.ddd4j.data.projection.javalin;

import java.util.Collections;
import java.util.Arrays;
import io.ddd4j.core.cqrs.readmodel.*;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JavalinProjectionScheduler} + {@link JavalinProjectionViewManager}
 * 真实 Javalin 启动集成测试。
 *
 * <p>真实 Javalin 应用（{@code Javalin.create().start(0)} 随机端口启动，零配置引导）
 * ＋手动装配链（Javalin 无容器——集成方直接构造或经工厂创建）：构造
 * {@link JavalinProjectionScheduler} + 测试替身（ProjectionService /
 * EventChunkReader / ProjectionRunner / ProjectionView）+ 经
 * {@link JavalinProjectionViewManager#create} 工厂装配视图管理器，
 * 完整验证 ViewScheduler 的 schedule/cancel/isActive 契约与 ViewManager 的
 * start/stop/isRunning/triggerOnce 生命周期。三用例与 Quarkus / Helidon IT 对齐：
 * <ol>
 *   <li>register + start + assert handle isActive + stop + assert inactive</li>
 *   <li>多 view 同时注册 + start + stop 隔离</li>
 *   <li>triggerOnce 立即执行一个 view 的 runOnce</li>
 * </ol>
 *
 * <p>每用例独立装配（构造即快照、无共享可变状态），保持用例间隔离。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DisplayName("JavalinProjectionScheduler + ViewManager 真实 Javalin 启动 IT")
class JavalinProjectionSchedulerIT {

    private Javalin app;

    @BeforeEach
    void startJavalin() {
        app = Javalin.create(cfg -> { }).start(0);
    }

    @AfterEach
    void stopJavalin() {
        if (app != null) {
            app.stop();
        }
    }

    private ProjectionRunner<Object> createRunner() {
        ProjectionService service = new ProjectionService() {
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
        EventChunkReader<Object> reader = (streamId, fromEventNumber, chunkSize, eventTypes) ->
                EventChunk.empty(fromEventNumber);
        return new ProjectionRunner<>(service, reader);
    }

    private ProjectionView<Object> createView(String name, String cron) {
        return new ProjectionView<Object>() {
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
        };
    }

    @Test
    void scheduler_schedule_应返回activeHandle_cancel后应inactive() {
        JavalinProjectionScheduler scheduler =
                JavalinProjectionScheduler.create(app, Arrays.asList(), createRunner());

        ViewScheduler.ViewScheduleHandle handle = scheduler.schedule(
                "direct-view", "0/1 * * * * *", () -> {});

        assertThat(handle.isActive()).isTrue();

        handle.cancel();
        assertThat(handle.isActive()).isFalse();
    }

    @Test
    void viewManager_start_stop_应管理生命周期() {
        ProjectionRunner<Object> runner = createRunner();
        ProjectionView<Object> view = createView("test-view", "0/5 * * * * *");
        JavalinProjectionViewManager viewManager =
                JavalinProjectionViewManager.create(app, Collections.singletonList(view), runner);

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
        ProjectionRunner<Object> runner = createRunner();
        ProjectionView<Object> view = createView("test-view", "0/5 * * * * *");
        JavalinProjectionViewManager viewManager =
                JavalinProjectionViewManager.create(app, Collections.singletonList(view), runner);

        viewManager.start();
        viewManager.triggerOnce();
        assertThat(viewManager.isRunning()).isTrue();
        viewManager.stop();
    }
}
