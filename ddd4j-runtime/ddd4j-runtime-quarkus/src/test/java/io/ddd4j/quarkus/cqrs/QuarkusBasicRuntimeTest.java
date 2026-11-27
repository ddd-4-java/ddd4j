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
    @Inject
    Instance<CommandBus> buses;
    @Inject
    Instance<ViewManager> managers;
    @Inject
    Instance<ProjectionRunner<?>> runners;
    @Inject
    io.quarkus.scheduler.Scheduler scheduler;
    @Inject
    io.quarkus.quartz.QuartzScheduler quartz;

    @Test
    void basicNeedsNoProjectionAndDoesNotRegisterJobs() throws Exception {
        assertThat(buses.isResolvable()).isTrue();
        assertThat(scheduler.getScheduledJobs()).isEmpty();
        assertThat(quartz.getScheduler().getJobKeys(org.quartz.impl.matchers.GroupMatcher.anyJobGroup())).isEmpty();
        assertThat(managers.get().isRunning()).isFalse();
    }
}
