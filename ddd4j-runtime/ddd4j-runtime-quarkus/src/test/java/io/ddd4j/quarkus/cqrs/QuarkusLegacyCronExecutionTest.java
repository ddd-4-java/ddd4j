package io.ddd4j.quarkus.cqrs;

import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 使用原配置表达式真实触发旧业务钩子，并检查关闭后不再调度。
 */
@QuarkusTest
@TestProfile(QuarkusLegacyCronExecutionTest.Profile.class)
class QuarkusLegacyCronExecutionTest {
    /** \u5f53\u524d\u6d4b\u8bd5\u8c03\u7528\u6216\u9a8c\u8bc1\u751f\u547d\u5468\u671f\u7684\u5e94\u7528\u670d\u52a1\u3002 */
    @Inject
    QuarkusLegacyServiceTest.LegacyBusinessService service;
    /** \u6d4b\u8bd5\u4f7f\u7528\u6216\u8bb0\u5f55\u7684\u591a\u6761\u6295\u5f71\u4f4d\u70b9\u3002 */
    @Inject
    ProjectionPositionRepository positions;
    /** \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u5e76\u8d1f\u8d23\u91ca\u653e\u7684\u89c6\u56fe\u6216\u8ba4\u8bc1\u7ba1\u7406\u5668\u3002 */
    @Inject
    QuarkusJpaViewManager manager;

    /**
     * \u9a8c\u8bc1<code>service.awaitCall()</code>\u4e3a\u771f\uff0c<code>service.calls()</code> \u5927\u4e8e <code>before</code>\uff0c<code>service.calls()</code> \u7b49\u4e8e <code>stopped</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void nativeQuartzTriggersAndCancellationStopsFurtherRuns() throws Exception {
        positions.save(new DefaultProjectionPosition("legacy-timer", 7));
        int before = service.calls();
        assertThat(service.awaitCall()).isTrue();
        assertThat(service.calls()).isGreaterThan(before);
        assertThat(QuarkusLegacyServiceTest.TrackedRequest.destroyed.await(4, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(QuarkusLegacyServiceTest.TrackedRequest.active.get()).isZero();
        assertThat(QuarkusLegacyServiceTest.TrackedRequest.creationThread).contains("QuartzScheduler_Worker");
        assertThat(QuarkusLegacyServiceTest.TrackedRequest.destructionThread)
                .isEqualTo(QuarkusLegacyServiceTest.TrackedRequest.creationThread);
        manager.stop();
        int stopped = service.calls();
        Thread.sleep(1200);
        assertThat(service.calls()).isEqualTo(stopped);
    }

    /** \u5b9e\u73b0 <code>QuarkusTestProfile</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>getConfigOverrides()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    public static class Profile implements QuarkusTestProfile {
        /**
         * \u542f\u7528\u9057\u7559\u6295\u5f71\u8c03\u5ea6\u8def\u5f84\u5e76\u8986\u76d6\u6d4b\u8bd5\u4f7f\u7528\u7684 Cron \u914d\u7f6e\u3002
         * @return \u8986\u76d6\u5230 Quarkus \u6d4b\u8bd5\u5e94\u7528\u7684\u914d\u7f6e\u9879
         */
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("r08.legacy-subclass", "true",
                    "ddd4j.quarkus.cqrs.projection.cron", "{r08.native-cron}",
                    "r08.native-cron", "0/1 * * * * ?");
        }
    }
}
