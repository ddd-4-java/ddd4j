package io.ddd4j.quarkus.cqrs;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.impl.matchers.GroupMatcher;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 旧配置表达式和禁用值必须保持原生语义。
 */
@QuarkusTest
@TestProfile(QuarkusDisabledProjectionTest.Profile.class)
class QuarkusDisabledProjectionTest {
    /** \u5f53\u524d\u6295\u5f71\u6d4b\u8bd5\u4f7f\u7528\u7684\u539f\u751f\u8c03\u5ea6\u5668\u6216\u66ff\u8eab\u3002 */
    @Inject
    Scheduler scheduler;
    /** \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u5e76\u8d1f\u8d23\u91ca\u653e\u7684\u89c6\u56fe\u6216\u8ba4\u8bc1\u7ba1\u7406\u5668\u3002 */
    @Inject
    QuarkusJpaViewManager manager;

    /**
     * \u9a8c\u8bc1<code>manager.isRunning()</code>\u4e3a\u5047\uff0c<code>scheduler.getJobKeys(GroupMatcher.anyJobGroup())</code>\u4e3a\u7a7a\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void originalDisabledExpressionDoesNotSchedule() throws Exception {
        assertThat(manager.isRunning()).isFalse();
        assertThat(scheduler.getJobKeys(GroupMatcher.anyJobGroup())).isEmpty();
    }

    /** \u5b9e\u73b0 <code>QuarkusTestProfile</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>getConfigOverrides()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    public static class Profile implements QuarkusTestProfile {
        /**
         * \u901a\u8fc7 <code>Map.of()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>Map.of("ddd4j.quarkus.cqrs.projection.cron", "{r08.disabled-cron}", "r08.disabled-cron", "disabled")</code>
         */
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("ddd4j.quarkus.cqrs.projection.cron", "{r08.disabled-cron}",
                    "r08.disabled-cron", "disabled");
        }
    }
}
