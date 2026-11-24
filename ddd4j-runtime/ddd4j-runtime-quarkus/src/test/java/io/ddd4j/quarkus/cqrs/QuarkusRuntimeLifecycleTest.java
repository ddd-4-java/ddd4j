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

    public static class LegacyCronProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Collections.singletonMap(QuarkusJpaViewManager.CRON_KEY, "0/1 * * * * ?");
        }
    }

    @Inject
    QuarkusJpaViewManager viewManager;

    @Inject
    Event<StartupEvent> startupEvents;

    @Inject
    Event<ShutdownEvent> shutdownEvents;

    @Test
    void startupEventStartsViewManager() {
        viewManager.stop();
        startupEvents.fire(new StartupEvent());
        assertThat(viewManager.isRunning()).isTrue();
    }

    @Test
    void shutdownEventStopsViewManager() {
        viewManager.start();
        shutdownEvents.fire(new ShutdownEvent());
        assertThat(viewManager.isRunning()).isFalse();
    }
}
