package io.ddd4j.quarkus.cqrs;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.impl.matchers.GroupMatcher;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

/** 旧配置表达式和禁用值必须保持原生语义。 */
@QuarkusTest
@TestProfile(QuarkusDisabledProjectionTest.Profile.class)
class QuarkusDisabledProjectionTest {
    public static class Profile implements QuarkusTestProfile {
        @Override public Map<String, String> getConfigOverrides() {
            return Map.of("ddd4j.quarkus.cqrs.projection.cron", "{r08.disabled-cron}",
                    "r08.disabled-cron", "disabled");
        }
    }
    @Inject Scheduler scheduler;
    @Inject QuarkusJpaViewManager manager;
    @Test
    void originalDisabledExpressionDoesNotSchedule() throws Exception {
        assertThat(manager.isRunning()).isFalse();
        assertThat(scheduler.getJobKeys(GroupMatcher.anyJobGroup())).isEmpty();
    }
}
