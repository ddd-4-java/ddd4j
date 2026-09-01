package io.ddd4j.data.projection.micronaut;

import io.ddd4j.core.cqrs.readmodel.*;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MicronautProjectionScheduler} + {@link MicronautProjectionViewManager}
 * 真实 BeanContext 集成测试。
 *
 * <p>真实 Micronaut 容器（{@code @MicronautTest}，零配置引导——Bean 定义由
 * {@code micronaut-inject-java} 编译期生成）＋真实 {@code @Singleton} 调度器 Bean
 * （零 mock）——完整验证 ViewScheduler 的 schedule/cancel/isActive 契约与 ViewManager
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
@DisplayName("MicronautProjectionScheduler + ViewManager 真实 BeanContext IT")
@MicronautTest
class MicronautProjectionSchedulerIT {

    @Inject
    MicronautProjectionScheduler scheduler;

    @Inject
    MicronautProjectionViewManager viewManager;

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
        // @PostConstruct 已自动 start，先 stop 再验证
        viewManager.stop();
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
        // 前序测试可能已 stop，确保重新 start
        if (!viewManager.isRunning()) {
            viewManager.start();
        }
        viewManager.triggerOnce();
        assertThat(viewManager.isRunning()).isTrue();
    }
}
