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
    /**
     * 当前投影测试使用的原生调度器或替身。
     */
    @Inject
    Scheduler scheduler;
    /**
     * 当前测试使用并负责释放的视图或认证管理器。
     */
    @Inject
    QuarkusJpaViewManager manager;

    /**
     * 验证<code>manager.isRunning()</code>为假，<code>scheduler.getJobKeys(GroupMatcher.anyJobGroup())</code>为空。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void originalDisabledExpressionDoesNotSchedule() throws Exception {
        assertThat(manager.isRunning()).isFalse();
        assertThat(scheduler.getJobKeys(GroupMatcher.anyJobGroup())).isEmpty();
    }

    /**
     * 实现 <code>QuarkusTestProfile</code>，供测试调用 <code>getConfigOverrides()</code> 并观察结果。
     */
    public static class Profile implements QuarkusTestProfile {
        /**
         * 通过 <code>Map.of()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>Map.of("ddd4j.quarkus.cqrs.projection.cron", "{r08.disabled-cron}", "r08.disabled-cron", "disabled")</code>
         */
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(QuarkusJpaViewManager.MODE_KEY, "legacy", "ddd4j.quarkus.cqrs.projection.cron", "{r08.disabled-cron}",
                    "r08.disabled-cron", "disabled");
        }
    }
}
