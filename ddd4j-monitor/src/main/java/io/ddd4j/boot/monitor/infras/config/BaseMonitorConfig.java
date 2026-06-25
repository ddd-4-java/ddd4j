package io.ddd4j.boot.monitor.infras.config;

import ch.qos.logback.classic.LoggerContext;
import io.ddd4j.boot.monitor.application.service.DingDingRobotSender;
import io.ddd4j.boot.monitor.application.service.QiWeiRobotSender;
import io.ddd4j.boot.monitor.domain.robot.service.RobotLogbackAppendService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 日志告警配置
 *
 * @author Jensen
 * @公众号 架构师修行录
 */
@Configuration
@EnableConfigurationProperties(BaseMonitorProperties.class)
@ConditionalOnClass(LoggerContext.class)
@ConditionalOnProperty(name = "base-monitor.log.enable", havingValue = "true", matchIfMissing = true)
public class BaseMonitorConfig {

    @Bean
    public RobotLogbackAppendService robotLogbackAppendService() {
        return new RobotLogbackAppendService();
    }

    @Bean
    @ConditionalOnProperty(name = "base-monitor.log.robot.qiwei.enable", havingValue = "true", matchIfMissing = true)
    public QiWeiRobotSender qiWeiRobotSender(BaseMonitorProperties properties) {
        return new QiWeiRobotSender(properties.getLog().getQiwei().getKey());
    }

    @Bean
    @ConditionalOnProperty(name = "base-monitor.log.robot.dingding.enable", havingValue = "true")
    public DingDingRobotSender dingDingRobotSender(BaseMonitorProperties properties) {
        return new DingDingRobotSender(properties.getLog().getDingding().getToken(), properties.getLog().getDingding().getSecret());
    }

}