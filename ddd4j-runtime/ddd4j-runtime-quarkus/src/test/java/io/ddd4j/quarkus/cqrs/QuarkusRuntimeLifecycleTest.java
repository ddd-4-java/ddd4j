package io.ddd4j.quarkus.cqrs;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 在真实 Quarkus 容器中验证读侧管理器的启动和停止事件。
 */
@QuarkusTest
@TestProfile(QuarkusRuntimeLifecycleTest.LegacyCronProfile.class)
class QuarkusRuntimeLifecycleTest {

    /** \u7ba1\u7406\u67e5\u8be2\u89c6\u56fe\u53ca\u5176\u6295\u5f71\u6267\u884c\u7684\u7ec4\u4ef6\u3002 */
    @Inject
    QuarkusJpaViewManager viewManager;
    /** \u6355\u83b7\u7ec4\u4ef6\u542f\u52a8\u65f6\u53d1\u5e03\u7684\u4e8b\u4ef6\u3002 */
    @Inject
    Event<StartupEvent> startupEvents;
    /** \u6355\u83b7\u8d44\u6e90\u505c\u6b62\u6216\u8bf7\u6c42\u7ed3\u675f\u7684\u751f\u547d\u5468\u671f\u4e8b\u4ef6\u3002 */
    @Inject
    Event<ShutdownEvent> shutdownEvents;

    /** \u9a8c\u8bc1<code>viewManager.isRunning()</code>\u4e3a\u771f\u3002 */
    @Test
    void startupEventStartsViewManager() {
        viewManager.stop();
        startupEvents.fire(new StartupEvent());
        assertThat(viewManager.isRunning()).isTrue();
    }

    /** \u9a8c\u8bc1<code>viewManager.isRunning()</code>\u4e3a\u5047\u3002 */
    @Test
    void shutdownEventStopsViewManager() {
        viewManager.start();
        shutdownEvents.fire(new ShutdownEvent());
        assertThat(viewManager.isRunning()).isFalse();
    }

    /** \u5b9e\u73b0 <code>QuarkusTestProfile</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>getConfigOverrides()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    public static class LegacyCronProfile implements QuarkusTestProfile {
        /**
         * \u901a\u8fc7 <code>Collections.singletonMap()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>Collections.singletonMap(QuarkusJpaViewManager.CRON_KEY, "0/1 * * * * ?")</code>
         */
        @Override
        public Map<String, String> getConfigOverrides() {
            return Collections.singletonMap(QuarkusJpaViewManager.CRON_KEY, "0/1 * * * * ?");
        }
    }
}
