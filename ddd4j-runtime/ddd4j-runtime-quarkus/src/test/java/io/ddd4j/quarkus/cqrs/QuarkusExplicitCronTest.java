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

    /** \u5f53\u524d\u6295\u5f71\u6d4b\u8bd5\u4f7f\u7528\u7684\u8c03\u5ea6\u8868\u8fbe\u5f0f\u3002 */
    private static final String CRON = "*/30 * * * * ?";
    /** \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u5e76\u8d1f\u8d23\u91ca\u653e\u7684\u89c6\u56fe\u6216\u8ba4\u8bc1\u7ba1\u7406\u5668\u3002 */
    @Inject
    QuarkusJpaViewManager manager;
    /** \u5f53\u524d\u6295\u5f71\u6d4b\u8bd5\u4f7f\u7528\u7684\u539f\u751f\u8c03\u5ea6\u5668\u6216\u66ff\u8eab\u3002 */
    @Inject
    Scheduler scheduler;

    /**
     * \u9a8c\u8bc1<code>scheduler.isStarted()</code>\u4e3a\u771f\uff0c<code>scheduler.isShutdown()</code>\u4e3a\u5047\u3002
     * @throws Exception \u51c6\u5907\u6216\u91ca\u653e\u88ab\u6d4b\u8fd0\u884c\u8d44\u6e90\u65f6\u5931\u8d25
     */
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

    /**
     * \u6838\u5bf9<code>keys</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\uff0c<code>trigger.getCronExpression()</code> \u7b49\u4e8e <code>CRON</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    private void assertOneCronJob() throws Exception {
        Set<JobKey> keys = scheduler.getJobKeys(GroupMatcher.anyJobGroup());
        assertThat(keys).hasSize(1);
        CronTrigger trigger = (CronTrigger) scheduler.getTriggersOfJob(keys.iterator().next()).get(0);
        assertThat(trigger.getCronExpression()).isEqualTo(CRON);
    }

    /** \u5b9e\u73b0 <code>QuarkusTestProfile</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>getConfigOverrides()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    public static class Profile implements QuarkusTestProfile {
        /**
         * \u901a\u8fc7 <code>Collections.singletonMap()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>Collections.singletonMap(QuarkusJpaViewManager.CRON_KEY, CRON)</code>
         */
        @Override
        public Map<String, String> getConfigOverrides() {
            return Collections.singletonMap(QuarkusJpaViewManager.CRON_KEY, CRON);
        }
    }
}
