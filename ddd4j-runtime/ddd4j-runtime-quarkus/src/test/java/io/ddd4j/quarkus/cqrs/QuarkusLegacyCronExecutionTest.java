package io.ddd4j.quarkus.cqrs;

import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import javax.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 使用原配置表达式真实触发旧业务钩子，并检查关闭后不再调度。
 */
@QuarkusTest
@TestProfile(QuarkusLegacyCronExecutionTest.Profile.class)
class QuarkusLegacyCronExecutionTest {
    /**
     * 当前测试调用或验证生命周期的应用服务。
     */
    @Inject
    javax.enterprise.inject.Instance<QuarkusLegacyServiceTest.LegacyBusinessService> service;
    /**
     * 测试使用或记录的多条投影位点。
     */
    @Inject
    ProjectionPositionRepository positions;
    /**
     * 当前测试使用并负责释放的视图或认证管理器。
     */
    @Inject
    QuarkusJpaViewManager manager;

    /**
     * 验证<code>service.get().awaitCall()</code>为真，<code>service.get().calls()</code> 大于 <code>before</code>，<code>service.get().calls()</code> 等于 <code>stopped</code>。
     *
     * @throws Exception 等待并发操作时失败
     */
    @Test
    void nativeQuartzTriggersAndCancellationStopsFurtherRuns() throws Exception {
        positions.save(new DefaultProjectionPosition("legacy-timer", 7));
        int before = service.get().calls();
        assertThat(service.get().awaitCall()).isTrue();
        assertThat(service.get().calls()).isGreaterThan(before);
        assertThat(QuarkusLegacyServiceTest.TrackedRequest.destroyed.await(4, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(QuarkusLegacyServiceTest.TrackedRequest.active.get()).isZero();
        assertThat(QuarkusLegacyServiceTest.TrackedRequest.creationThread).contains("QuartzScheduler_Worker");
        assertThat(QuarkusLegacyServiceTest.TrackedRequest.destructionThread)
                .isEqualTo(QuarkusLegacyServiceTest.TrackedRequest.creationThread);
        manager.stop();
        int stopped = service.get().calls();
        Thread.sleep(1200);
        assertThat(service.get().calls()).isEqualTo(stopped);
    }

    /**
     * 实现 <code>QuarkusTestProfile</code>，供测试调用 <code>getConfigOverrides()</code> 并观察结果。
     */
    public static class Profile implements QuarkusTestProfile {
        /**
         * 启用遗留投影调度路径并覆盖测试使用的 Cron 配置。
         *
         * @return 覆盖到 Quarkus 测试应用的配置项
         */
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> values = new java.util.HashMap<>();
            values.put("r08.legacy-subclass", "true");
            values.put("ddd4j.quarkus.cqrs.projection.cron", "{r08.native-cron}");
            values.put("r08.native-cron", "0/1 * * * * ?");
            values.put(QuarkusJpaViewManager.MODE_KEY, "legacy");
            return values;
        }
    }
}
