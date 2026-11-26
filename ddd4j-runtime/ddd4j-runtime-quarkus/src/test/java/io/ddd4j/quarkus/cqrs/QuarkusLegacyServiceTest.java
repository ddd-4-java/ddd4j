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
    @Inject
    Scheduler scheduler;
    @Inject
    QuarkusJpaViewManager manager;
    @Inject
    LegacyBusinessService service;
    @Inject
    io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository positions;

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

    public static class Profile implements QuarkusTestProfile {
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
        static final java.util.concurrent.atomic.AtomicInteger active = new java.util.concurrent.atomic.AtomicInteger();
        static final java.util.concurrent.CountDownLatch destroyed = new java.util.concurrent.CountDownLatch(1);
        static volatile String creationThread;
        static volatile String destructionThread;

        @jakarta.annotation.PostConstruct
        void create() {
            creationThread = Thread.currentThread().getName();
            active.incrementAndGet();
        }

        public void touch() {
        }

        @jakarta.annotation.PreDestroy
        void destroy() {
            destructionThread = Thread.currentThread().getName();
            active.decrementAndGet();
            destroyed.countDown();
        }
    }

    @ApplicationScoped
    @IfBuildProperty(name = "r08.legacy-subclass", stringValue = "true")
    public static class LegacyBusinessService extends QuarkusJpaProjectionService {
        private final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.concurrent.CountDownLatch called = new java.util.concurrent.CountDownLatch(1);
        @Inject
        TrackedRequest request;

        @Override
        protected void pullAndApply(String streamId) {
            request.touch();
            calls.incrementAndGet();
            called.countDown();
        }

        public boolean awaitCall() throws InterruptedException {
            return called.await(4, java.util.concurrent.TimeUnit.SECONDS);
        }

        public int calls() {
            return calls.get();
        }
    }
}
