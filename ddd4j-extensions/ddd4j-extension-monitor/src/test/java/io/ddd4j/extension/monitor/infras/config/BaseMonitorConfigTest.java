package io.ddd4j.extension.monitor.infras.config;

import io.ddd4j.extension.monitor.application.service.DingDingRobotSender;
import io.ddd4j.extension.monitor.application.service.QiWeiRobotSender;
import io.ddd4j.extension.monitor.domain.robot.service.RobotLogbackAppendService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BaseMonitorConfig} plain Spring configuration tests.
 */
class BaseMonitorConfigTest {

    @Test
    void defaultPropertiesShouldKeepMonitorDefaults() {
        BaseMonitorProperties properties = new BaseMonitorProperties();

        assertThat(properties.getLog().isEnable()).isTrue();
        assertThat(properties.getLog().getRateLimiterPermitsPerSecond()).isEqualTo(0.2857);
        assertThat(properties.getLog().getDingding().isEnable()).isTrue();
        assertThat(properties.getLog().getQiwei().isEnable()).isTrue();
    }

    @Test
    void baseMonitorConfigShouldCreateCoreBeans() {
        BaseMonitorConfig config = new BaseMonitorConfig();
        BaseMonitorProperties properties = new BaseMonitorProperties();

        assertThat(config.baseMonitorProperties()).isInstanceOf(BaseMonitorProperties.class);
        assertThat(config.robotLogbackAppendService()).isInstanceOf(RobotLogbackAppendService.class);
        assertThat(config.qiWeiRobotSender(properties)).isInstanceOf(QiWeiRobotSender.class);
        assertThat(config.dingDingRobotSender(properties)).isInstanceOf(DingDingRobotSender.class);
    }
}
