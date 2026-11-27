package io.ddd4j.quarkus.cqrs;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import javax.inject.Inject;
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
 * 显式选择 legacy 后，旧 cron 可使用 Quarkus 的原生 Quartz 调度。
 */
@QuarkusTest
@TestProfile(QuarkusExplicitCronTest.Profile.class)
class QuarkusExplicitCronTest {

    /**
     * 当前投影测试使用的调度表达式。
     */
    private static final String CRON = "*/30 * * * * ?";
    /**
     * 当前测试使用并负责释放的视图或认证管理器。
     */
    @Inject
    QuarkusJpaViewManager manager;
    /**
     * 当前投影测试使用的原生调度器或替身。
     */
    @Inject
    Scheduler scheduler;

    /**
     * 验证<code>scheduler.isStarted()</code>为真，<code>scheduler.isShutdown()</code>为假。
     *
     * @throws Exception 准备或释放被测运行资源时失败
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
     * 核对<code>keys</code> 的大小为 <code>1</code>，<code>trigger.getCronExpression()</code> 等于 <code>CRON</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    private void assertOneCronJob() throws Exception {
        Set<JobKey> keys = scheduler.getJobKeys(GroupMatcher.anyJobGroup());
        assertThat(keys).hasSize(1);
        CronTrigger trigger = (CronTrigger) scheduler.getTriggersOfJob(keys.iterator().next()).get(0);
        assertThat(trigger.getCronExpression()).isEqualTo(CRON);
    }

    /**
     * 实现 <code>QuarkusTestProfile</code>，供测试调用 <code>getConfigOverrides()</code> 并观察结果。
     */
    public static class Profile implements QuarkusTestProfile {
        /**
         * 通过 <code>Collections.singletonMap()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>Collections.singletonMap(QuarkusJpaViewManager.CRON_KEY, CRON)</code>
         */
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> values = new java.util.HashMap<>();
            values.put(QuarkusJpaViewManager.CRON_KEY, CRON);
            values.put(QuarkusJpaViewManager.MODE_KEY, "legacy");
            return values;
        }
    }
}
