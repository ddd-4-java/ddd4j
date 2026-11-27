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
    @Inject
    QuarkusLegacyServiceTest.LegacyBusinessService service;
    @Inject
    ProjectionPositionRepository positions;
    @Inject
    QuarkusJpaViewManager manager;

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

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("r08.legacy-subclass", "true",
                    "ddd4j.quarkus.cqrs.projection.cron", "{r08.native-cron}",
                    "r08.native-cron", "0/1 * * * * ?");
        }
    }
}
