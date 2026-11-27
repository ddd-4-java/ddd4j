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
    /** \u4f9b\u53c2\u6570\u5316\u6d4b\u8bd5\u8986\u76d6\u7684\u591a\u4e2a\u5b9e\u9645\u603b\u7ebf\u5b9e\u73b0\u3002 */
    @Inject
    Instance<CommandBus> buses;
    /** \u63d0\u4f9b\u7ed9\u5bb9\u5668\u9009\u62e9\u6d4b\u8bd5\u7ba1\u7406\u5668\u7684\u5019\u9009\u96c6\u5408\u3002 */
    @Inject
    Instance<ViewManager> managers;
    /** \u5bb9\u5668\u53ef\u89e3\u6790\u7684\u591a\u4e2a\u6295\u5f71\u8fd0\u884c\u5668\u5019\u9009\u3002 */
    @Inject
    Instance<ProjectionRunner<?>> runners;
    /** \u5f53\u524d\u6295\u5f71\u6d4b\u8bd5\u4f7f\u7528\u7684\u539f\u751f\u8c03\u5ea6\u5668\u6216\u66ff\u8eab\u3002 */
    @Inject
    io.quarkus.scheduler.Scheduler scheduler;
    /** \u6d4b\u8bd5\u6295\u5f71\u89c6\u56fe\u4f7f\u7528\u7684 Quartz \u8c03\u5ea6\u5668\u6216\u5176 CDI \u5b9e\u4f8b\u3002 */
    @Inject
    io.quarkus.quartz.QuartzScheduler quartz;

    /**
     * \u9a8c\u8bc1<code>buses.isResolvable()</code>\u4e3a\u771f\uff0c<code>scheduler.getScheduledJobs()</code>\u4e3a\u7a7a\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void basicNeedsNoProjectionAndDoesNotRegisterJobs() throws Exception {
        assertThat(buses.isResolvable()).isTrue();
        assertThat(scheduler.getScheduledJobs()).isEmpty();
        assertThat(quartz.getScheduler().getJobKeys(org.quartz.impl.matchers.GroupMatcher.anyJobGroup())).isEmpty();
        assertThat(managers.get().isRunning()).isFalse();
    }
}
