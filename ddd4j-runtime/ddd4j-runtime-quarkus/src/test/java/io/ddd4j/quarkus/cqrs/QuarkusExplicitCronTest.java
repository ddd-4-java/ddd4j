package io.ddd4j.quarkus.cqrs;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.impl.matchers.GroupMatcher;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 显式配置旧 cron 即可使用 Quarkus 的原生 Quartz 调度。
 */
@QuarkusTest
@TestProfile(QuarkusExplicitCronTest.Profile.class)
class QuarkusExplicitCronTest {

    private static final String CRON = "*/30 * * * * ?";
    @Inject
    QuarkusJpaViewManager manager;
    @Inject
    Scheduler scheduler;

    @Test
    void explicitCronRegistersOneJobAndReleasesItOnStop() throws Exception {
        try {
            assertThat(scheduler.isStarted()).isTrue();
            assertThat(manager.isRunning()).isTrue();
            assertOneCronJob();

            manager.stop();
            assertThat(scheduler.getJobKeys(GroupMatcher.anyJobGroup())).isEmpty();
            assertThat(scheduler.isShutdown()).isFalse();

            manager.start();
            assertOneCronJob();
        } finally {
            manager.stop();
        }
        assertThat(scheduler.getJobKeys(GroupMatcher.anyJobGroup())).isEmpty();
        assertThat(scheduler.isShutdown()).isFalse();
    }

    private void assertOneCronJob() throws Exception {
        Set<JobKey> keys = scheduler.getJobKeys(GroupMatcher.anyJobGroup());
        assertThat(keys).hasSize(1);
        CronTrigger trigger = (CronTrigger) scheduler.getTriggersOfJob(keys.iterator().next()).get(0);
        assertThat(trigger.getCronExpression()).isEqualTo(CRON);
    }

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Collections.singletonMap(QuarkusJpaViewManager.CRON_KEY, CRON);
        }
    }
}
