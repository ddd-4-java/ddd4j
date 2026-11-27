package io.ddd4j.quarkus.cqrs;

import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 无业务执行器、事件源及数据库的真实 ArC 基础运行时。
 */
@QuarkusTest
class QuarkusBasicRuntimeTest {
    /**
     * 供参数化测试覆盖的多个实际总线实现。
     */
    @Inject
    Instance<CommandBus> buses;
    /**
     * 提供给容器选择测试管理器的候选集合。
     */
    @Inject
    Instance<ViewManager> managers;
    /**
     * 容器可解析的多个投影运行器候选。
     */
    @Inject
    Instance<ProjectionRunner<?>> runners;
    /**
     * 当前投影测试使用的原生调度器或替身。
     */
    @Inject
    io.quarkus.scheduler.Scheduler scheduler;
    /**
     * 测试投影视图使用的 Quartz 调度器或其 CDI 实例。
     */
    @Inject
    io.quarkus.quartz.QuartzScheduler quartz;

    /**
     * 验证<code>buses.isResolvable()</code>为真，<code>scheduler.getScheduledJobs()</code>为空。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void basicNeedsNoProjectionAndDoesNotRegisterJobs() throws Exception {
        assertThat(buses.isResolvable()).isTrue();
        assertThat(scheduler.getScheduledJobs()).isEmpty();
        assertThat(quartz.getScheduler().getJobKeys(org.quartz.impl.matchers.GroupMatcher.anyJobGroup())).isEmpty();
        assertThat(managers.get().isRunning()).isFalse();
    }
}
