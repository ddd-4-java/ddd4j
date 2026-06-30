package io.ddd4j.extension.monitor.infras.config;

import io.ddd4j.extension.monitor.application.service.DingDingRobotSender;
import io.ddd4j.extension.monitor.application.service.QiWeiRobotSender;
import io.ddd4j.extension.monitor.domain.robot.service.RobotLogbackAppendService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BaseMonitorConfig} configuration tests.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class BaseMonitorConfigTest {

    @Test
    void baseMonitorPropertiesShouldBind() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesOnlyConfig.class)
                .withPropertyValues(
                        "base-monitor.log.rate-limiter-permits-per-second=1.5",
                        "base-monitor.log.dingding.enable=false",
                        "base-monitor.log.qiwei.key=qw-key")
                .run(context -> {
                    BaseMonitorProperties properties = context.getBean(BaseMonitorProperties.class);
                    assertEquals(1.5, properties.getLog().getRateLimiterPermitsPerSecond());
                    assertFalse(properties.getLog().getDingding().isEnable());
                    assertEquals("qw-key", properties.getLog().getQiwei().getKey());
                });
    }

    @Test
    void logDisabledShouldNotRegisterAlertBeans() {
        new ApplicationContextRunner()
                .withUserConfiguration(BaseMonitorConfig.class)
                .withPropertyValues("base-monitor.log.enable=false")
                .run(context -> {
                    assertFalse(context.containsBean("robotLogbackAppendService"));
                    assertFalse(context.containsBean("qiWeiRobotSender"));
                    assertFalse(context.containsBean("dingDingRobotSender"));
                    assertFalse(context.getBeanNamesForType(RobotLogbackAppendService.class).length > 0);
                });
    }

    @Test
    void robotSwitchesShouldDisableSenderBeans() {
        new ApplicationContextRunner()
                .withUserConfiguration(SenderOnlyConfig.class)
                .withPropertyValues(
                        "base-monitor.log.qiwei.enable=false",
                        "base-monitor.log.dingding.enable=false")
                .run(context -> {
                    assertTrue(context.getBeanNamesForType(BaseMonitorProperties.class).length > 0);
                    assertFalse(context.getBeanNamesForType(QiWeiRobotSender.class).length > 0);
                    assertFalse(context.getBeanNamesForType(DingDingRobotSender.class).length > 0);
                });
    }

    @Configuration
    @EnableConfigurationProperties(BaseMonitorProperties.class)
    static class PropertiesOnlyConfig {
    }

    @Configuration
    @EnableConfigurationProperties(BaseMonitorProperties.class)
    static class SenderOnlyConfig {

        @org.springframework.context.annotation.Bean
        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                prefix = "base-monitor.log.qiwei",
                name = "enable",
                havingValue = "true",
                matchIfMissing = true)
        QiWeiRobotSender qiWeiRobotSender(BaseMonitorProperties properties) {
            return new QiWeiRobotSender(properties.getLog().getQiwei().getKey());
        }

        @org.springframework.context.annotation.Bean
        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                prefix = "base-monitor.log.dingding",
                name = "enable",
                havingValue = "true",
                matchIfMissing = true)
        DingDingRobotSender dingDingRobotSender(BaseMonitorProperties properties) {
            return new DingDingRobotSender(
                    properties.getLog().getDingding().getToken(),
                    properties.getLog().getDingding().getSecret());
        }
    }
}
