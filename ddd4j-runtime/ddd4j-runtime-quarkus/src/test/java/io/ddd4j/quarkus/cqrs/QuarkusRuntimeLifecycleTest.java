package io.ddd4j.quarkus.cqrs;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 在真实 Quarkus 容器中验证读侧管理器的启动和停止事件。
 */
@QuarkusTest
@TestProfile(QuarkusRuntimeLifecycleTest.LegacyCronProfile.class)
class QuarkusRuntimeLifecycleTest {

    /**
     * 管理查询视图及其投影执行的组件。
     */
    @Inject
    QuarkusJpaViewManager viewManager;
    /**
     * 捕获组件启动时发布的事件。
     */
    @Inject
    Event<StartupEvent> startupEvents;
    /**
     * 捕获资源停止或请求结束的生命周期事件。
     */
    @Inject
    Event<ShutdownEvent> shutdownEvents;

    /**
     * 验证<code>viewManager.isRunning()</code>为真。
     */
    @Test
    void startupEventStartsViewManager() {
        viewManager.stop();
        startupEvents.fire(new StartupEvent());
        assertThat(viewManager.isRunning()).isTrue();
    }

    /**
     * 验证<code>viewManager.isRunning()</code>为假。
     */
    @Test
    void shutdownEventStopsViewManager() {
        viewManager.start();
        shutdownEvents.fire(new ShutdownEvent());
        assertThat(viewManager.isRunning()).isFalse();
    }

    /**
     * 实现 <code>QuarkusTestProfile</code>，供测试调用 <code>getConfigOverrides()</code> 并观察结果。
     */
    public static class LegacyCronProfile implements QuarkusTestProfile {
        /**
         * 通过 <code>Collections.singletonMap()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>Collections.singletonMap(QuarkusJpaViewManager.CRON_KEY, "0/1 * * * * ?")</code>
         */
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(QuarkusJpaViewManager.MODE_KEY, "legacy",
                    QuarkusJpaViewManager.CRON_KEY, "0/1 * * * * ?");
        }
    }
}
