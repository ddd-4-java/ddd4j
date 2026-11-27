package io.ddd4j.quarkus.cqrs;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.Scheduler;
import org.quartz.impl.matchers.GroupMatcher;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实业务子类无需新增 mode 属性，仍以旧默认 cron 启动。
 */
@QuarkusTest
@TestProfile(QuarkusLegacyServiceTest.Profile.class)
class QuarkusLegacyServiceTest {
    /**
     * 当前投影测试使用的原生调度器或替身。
     */
    @Inject
    Scheduler scheduler;
    /**
     * 当前测试使用并负责释放的视图或认证管理器。
     */
    @Inject
    QuarkusJpaViewManager manager;
    /**
     * 当前测试调用或验证生命周期的应用服务。
     */
    @Inject
    LegacyBusinessService service;
    /**
     * 测试使用或记录的多条投影位点。
     */
    @Inject
    io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository positions;

    /**
     * 验证<code>manager.isRunning()</code>为真，<code>scheduler.isShutdown()</code>为假。
     *
     * @throws Exception 准备或释放被测运行资源时失败
     */
    @Test
    void legacySubclassUsesOriginalDefaultAndOnlyOneJob() throws Exception {
        assertThat(manager.isRunning()).isTrue();
        var keys = scheduler.getJobKeys(GroupMatcher.anyJobGroup());
        assertThat(keys).hasSize(1);
        CronTrigger trigger = (CronTrigger) scheduler.getTriggersOfJob(keys.iterator().next()).get(0);
        assertThat(trigger.getCronExpression()).isEqualTo("*/30 * * * * ?");
        Runnable scheduledTask = RunnableHolder.get(scheduler.getJobDetail(keys.iterator().next())
                .getJobDataMap().getString(QuarkusViewScheduler.TASK_KEY));
        manager.stop();
        positions.save(new io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition("legacy-hook", 7));
        var requestContext = io.quarkus.arc.Arc.container().requestContext();
        assertThat(requestContext.isActive()).isTrue();
        var inheritedState = requestContext.getState();
        scheduledTask.run();
        assertThat(requestContext.isActive()).isTrue();
        assertThat(requestContext.getState()).isSameAs(inheritedState);
        int before = service.calls();
        manager.triggerOnce();
        assertThat(service.calls()).isEqualTo(before + 1);
        assertThat(scheduler.getJobKeys(GroupMatcher.anyJobGroup())).isEmpty();
        assertThat(scheduler.isShutdown()).isFalse();
    }

    /**
     * 实现 <code>QuarkusTestProfile</code>，供测试调用 <code>getConfigOverrides()</code> 并观察结果。
     */
    public static class Profile implements QuarkusTestProfile {
        /**
         * 通过 <code>Map.of()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>Map.of("r08.legacy-subclass", "true")</code>
         */
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("r08.legacy-subclass", "true");
        }
    }

    /**
     * 真正的请求作用域对象；由定时线程创建并由该调用的原生上下文销毁。
     */
    @jakarta.enterprise.context.RequestScoped
    @IfBuildProperty(name = "r08.legacy-subclass", stringValue = "true")
    public static class TrackedRequest {
        /**
         * 在测试线程之间共享当前操作次数或更新数量的计数器。
         */
        static final java.util.concurrent.atomic.AtomicInteger active = new java.util.concurrent.atomic.AtomicInteger();
        /**
         * 协调测试线程在指定执行阶段等待或继续的门闩。
         */
        static final java.util.concurrent.CountDownLatch destroyed = new java.util.concurrent.CountDownLatch(1);
        /**
         * 记录组件实际创建时所在线程的名称。
         */
        static volatile String creationThread;
        /**
         * 实际执行资源销毁时所在线程的名称。
         */
        static volatile String destructionThread;

        /**
         * 根据本次调用更新 <code>creationThread = Thread.currentThread().getName()</code>，用于后续测试步骤。
         */
        @jakarta.annotation.PostConstruct
        void create() {
            creationThread = Thread.currentThread().getName();
            active.incrementAndGet();
        }

        /**
         * 作为空实现的测试替身，调用时保持当前夹具状态。
         */
        public void touch() {
        }

        /**
         * 根据本次调用更新 <code>destructionThread = Thread.currentThread().getName()</code>，用于后续测试步骤。
         */
        @jakarta.annotation.PreDestroy
        void destroy() {
            destructionThread = Thread.currentThread().getName();
            active.decrementAndGet();
            destroyed.countDown();
        }
    }

    /**
     * 向测试容器注册 <code>pullAndApply()</code>、<code>awaitCall()</code>、<code>calls()</code> 等测试入口或组件。
     */
    @ApplicationScoped
    @IfBuildProperty(name = "r08.legacy-subclass", stringValue = "true")
    public static class LegacyBusinessService extends QuarkusJpaProjectionService {
        /**
         * 记录被测入口实际触发的调用。
         */
        private final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        /**
         * 协调测试线程在指定执行阶段等待或继续的门闩。
         */
        private final java.util.concurrent.CountDownLatch called = new java.util.concurrent.CountDownLatch(1);
        /**
         * 当前测试提交给框架的 HTTP 请求。
         */
        @Inject
        TrackedRequest request;

        /**
         * 拉取投影尚未处理的事件并更新读模型。
         *
         * <p>测试夹具会调用 <code>request.touch()</code>、<code>calls.incrementAndGet()</code>。</p>
         *
         * @param streamId 投影流标识
         */
        @Override
        protected void pullAndApply(String streamId) {
            request.touch();
            calls.incrementAndGet();
            called.countDown();
        }

        /**
         * 通过 <code>called.await()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>called.await(4, java.util.concurrent.TimeUnit.SECONDS)</code>
         * @throws InterruptedException 等待测试操作时被中断
         */
        public boolean awaitCall() throws InterruptedException {
            return called.await(4, java.util.concurrent.TimeUnit.SECONDS);
        }

        /**
         * 通过 <code>calls.get()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>calls.get()</code>
         */
        public int calls() {
            return calls.get();
        }
    }
}
