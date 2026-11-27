package io.ddd4j.quarkus.cqrs;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import javax.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证旧配置和业务子类同时存在时仍须显式启用投影。 */
@QuarkusTest
@TestProfile(QuarkusImplicitProjectionTest.Profile.class)
class QuarkusImplicitProjectionTest {
    /** 实际接收容器启动事件的视图管理器。 */
    @Inject
    QuarkusJpaViewManager manager;

    /** 旧应用升级后缺少模式配置时没有后台任务。 */
    @Test
    void cronAndBusinessServiceAloneDoNotStartProjection() {
        assertThat(manager.isRunning()).isFalse();
    }

    /** 模拟只有旧 cron 和自定义投影服务的应用。 */
    public static class Profile implements QuarkusTestProfile {
        /** @return 启用旧业务服务并指定调度间隔的测试配置 */
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> settings = new HashMap<>();
            settings.put("r08.legacy-subclass", "true");
            settings.put(QuarkusJpaViewManager.CRON_KEY, "0/1 * * * * ?");
            return settings;
        }
    }
}
