package io.ddd4j.extension.monitor.infras.config;

import ch.qos.logback.classic.LoggerContext;
import io.ddd4j.extension.monitor.application.service.DingDingRobotSender;
import io.ddd4j.extension.monitor.application.service.QiWeiRobotSender;
import io.ddd4j.extension.monitor.domain.robot.service.RobotLogbackAppendService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 日志告警配置
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Configuration
@EnableConfigurationProperties(BaseMonitorProperties.class)
@ConditionalOnClass(LoggerContext.class)
@ConditionalOnProperty(prefix = "base-monitor.log", name = "enable", havingValue = "true", matchIfMissing = true)
public class BaseMonitorConfig {

    @Bean
    public RobotLogbackAppendService robotLogbackAppendService() {
        return new RobotLogbackAppendService();
    }

    @Bean
    @ConditionalOnProperty(prefix = "base-monitor.log.qiwei", name = "enable", havingValue = "true", matchIfMissing = true)
    public QiWeiRobotSender qiWeiRobotSender(BaseMonitorProperties properties) {
        return new QiWeiRobotSender(properties.getLog().getQiwei().getKey());
    }

    @Bean
    @ConditionalOnProperty(prefix = "base-monitor.log.dingding", name = "enable", havingValue = "true", matchIfMissing = true)
    public DingDingRobotSender dingDingRobotSender(BaseMonitorProperties properties) {
        return new DingDingRobotSender(properties.getLog().getDingding().getToken(), properties.getLog().getDingding().getSecret());
    }

}
