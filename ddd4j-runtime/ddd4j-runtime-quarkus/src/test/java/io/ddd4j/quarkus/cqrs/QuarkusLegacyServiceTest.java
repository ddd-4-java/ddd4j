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
    /** \u5f53\u524d\u6295\u5f71\u6d4b\u8bd5\u4f7f\u7528\u7684\u539f\u751f\u8c03\u5ea6\u5668\u6216\u66ff\u8eab\u3002 */
    @Inject
    Scheduler scheduler;
    /** \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u5e76\u8d1f\u8d23\u91ca\u653e\u7684\u89c6\u56fe\u6216\u8ba4\u8bc1\u7ba1\u7406\u5668\u3002 */
    @Inject
    QuarkusJpaViewManager manager;
    /** \u5f53\u524d\u6d4b\u8bd5\u8c03\u7528\u6216\u9a8c\u8bc1\u751f\u547d\u5468\u671f\u7684\u5e94\u7528\u670d\u52a1\u3002 */
    @Inject
    LegacyBusinessService service;
    /** \u6d4b\u8bd5\u4f7f\u7528\u6216\u8bb0\u5f55\u7684\u591a\u6761\u6295\u5f71\u4f4d\u70b9\u3002 */
    @Inject
    io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository positions;

    /**
     * \u9a8c\u8bc1<code>manager.isRunning()</code>\u4e3a\u771f\uff0c<code>scheduler.isShutdown()</code>\u4e3a\u5047\u3002
     * @throws Exception \u51c6\u5907\u6216\u91ca\u653e\u88ab\u6d4b\u8fd0\u884c\u8d44\u6e90\u65f6\u5931\u8d25
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

    /** \u5b9e\u73b0 <code>QuarkusTestProfile</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>getConfigOverrides()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    public static class Profile implements QuarkusTestProfile {
        /**
         * \u901a\u8fc7 <code>Map.of()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>Map.of("r08.legacy-subclass", "true")</code>
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
        /** \u5728\u6d4b\u8bd5\u7ebf\u7a0b\u4e4b\u95f4\u5171\u4eab\u5f53\u524d\u64cd\u4f5c\u6b21\u6570\u6216\u66f4\u65b0\u6570\u91cf\u7684\u8ba1\u6570\u5668\u3002 */
        static final java.util.concurrent.atomic.AtomicInteger active = new java.util.concurrent.atomic.AtomicInteger();
        /** \u534f\u8c03\u6d4b\u8bd5\u7ebf\u7a0b\u5728\u6307\u5b9a\u6267\u884c\u9636\u6bb5\u7b49\u5f85\u6216\u7ee7\u7eed\u7684\u95e8\u95e9\u3002 */
        static final java.util.concurrent.CountDownLatch destroyed = new java.util.concurrent.CountDownLatch(1);
        /** \u8bb0\u5f55\u7ec4\u4ef6\u5b9e\u9645\u521b\u5efa\u65f6\u6240\u5728\u7ebf\u7a0b\u7684\u540d\u79f0\u3002 */
        static volatile String creationThread;
        /** \u5b9e\u9645\u6267\u884c\u8d44\u6e90\u9500\u6bc1\u65f6\u6240\u5728\u7ebf\u7a0b\u7684\u540d\u79f0\u3002 */
        static volatile String destructionThread;

        /** \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>creationThread = Thread.currentThread().getName()</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002 */
        @jakarta.annotation.PostConstruct
        void create() {
            creationThread = Thread.currentThread().getName();
            active.incrementAndGet();
        }

        /** \u4f5c\u4e3a\u7a7a\u5b9e\u73b0\u7684\u6d4b\u8bd5\u66ff\u8eab\uff0c\u8c03\u7528\u65f6\u4fdd\u6301\u5f53\u524d\u5939\u5177\u72b6\u6001\u3002 */
        public void touch() {
        }

        /** \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>destructionThread = Thread.currentThread().getName()</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002 */
        @jakarta.annotation.PreDestroy
        void destroy() {
            destructionThread = Thread.currentThread().getName();
            active.decrementAndGet();
            destroyed.countDown();
        }
    }

    /** \u5411\u6d4b\u8bd5\u5bb9\u5668\u6ce8\u518c <code>pullAndApply()</code>\u3001<code>awaitCall()</code>\u3001<code>calls()</code> \u7b49\u6d4b\u8bd5\u5165\u53e3\u6216\u7ec4\u4ef6\u3002 */
    @ApplicationScoped
    @IfBuildProperty(name = "r08.legacy-subclass", stringValue = "true")
    public static class LegacyBusinessService extends QuarkusJpaProjectionService {
        /** \u8bb0\u5f55\u88ab\u6d4b\u5165\u53e3\u5b9e\u9645\u89e6\u53d1\u7684\u8c03\u7528\u3002 */
        private final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        /** \u534f\u8c03\u6d4b\u8bd5\u7ebf\u7a0b\u5728\u6307\u5b9a\u6267\u884c\u9636\u6bb5\u7b49\u5f85\u6216\u7ee7\u7eed\u7684\u95e8\u95e9\u3002 */
        private final java.util.concurrent.CountDownLatch called = new java.util.concurrent.CountDownLatch(1);
        /** \u5f53\u524d\u6d4b\u8bd5\u63d0\u4ea4\u7ed9\u6846\u67b6\u7684 HTTP \u8bf7\u6c42\u3002 */
        @Inject
        TrackedRequest request;

        /** {@inheritDoc} */
        @Override
        protected void pullAndApply(String streamId) {
            request.touch();
            calls.incrementAndGet();
            called.countDown();
        }

        /**
         * \u901a\u8fc7 <code>called.await()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>called.await(4, java.util.concurrent.TimeUnit.SECONDS)</code>
         * @throws InterruptedException \u7b49\u5f85\u6d4b\u8bd5\u64cd\u4f5c\u65f6\u88ab\u4e2d\u65ad
         */
        public boolean awaitCall() throws InterruptedException {
            return called.await(4, java.util.concurrent.TimeUnit.SECONDS);
        }

        /**
         * \u901a\u8fc7 <code>calls.get()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>calls.get()</code>
         */
        public int calls() {
            return calls.get();
        }
    }
}
