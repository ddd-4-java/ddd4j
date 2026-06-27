package io.ddd4j.extension.monitor.infras.config;

import io.ddd4j.extension.monitor.application.service.DingDingRobotSender;
import io.ddd4j.extension.monitor.application.service.QiWeiRobotSender;
import io.ddd4j.extension.monitor.domain.robot.service.RobotLogbackAppendService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 日志告警配置
 *
 * @author Jensen
 * @公众号 架构师修行录
 */
@Configuration
// @EnableConfigurationProperties(BaseMonitorProperties.class)
public class BaseMonitorConfig {

    @Bean
    public RobotLogbackAppendService robotLogbackAppendService() {
        return new RobotLogbackAppendService();
    }

    @Bean
    public QiWeiRobotSender qiWeiRobotSender(BaseMonitorProperties properties) {
        return new QiWeiRobotSender(properties.getLog().getQiwei().getKey());
    }

    @Bean
    public DingDingRobotSender dingDingRobotSender(BaseMonitorProperties properties) {
        return new DingDingRobotSender(properties.getLog().getDingding().getToken(), properties.getLog().getDingding().getSecret());
    }

}