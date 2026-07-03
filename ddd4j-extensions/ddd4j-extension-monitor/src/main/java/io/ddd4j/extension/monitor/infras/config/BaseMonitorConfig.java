package io.ddd4j.extension.monitor.infras.config;

import io.ddd4j.extension.monitor.application.service.DingDingRobotSender;
import io.ddd4j.extension.monitor.application.service.QiWeiRobotSender;
import io.ddd4j.extension.monitor.domain.robot.service.RobotLogbackAppendService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 日志告警配置
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Configuration
public class BaseMonitorConfig {

    @Bean
    public BaseMonitorProperties baseMonitorProperties() {
        return new BaseMonitorProperties();
    }

    @Bean
    public RobotLogbackAppendService robotLogbackAppendService() {
        return new RobotLogbackAppendService();
    }

    /**
     * 创建企微机器人发送器 Bean
     *
     * @param properties 监控配置属性
     * @return 企微机器人发送器实例
     */
    @Bean
    public QiWeiRobotSender qiWeiRobotSender(BaseMonitorProperties properties) {
        return new QiWeiRobotSender(properties.getLog().getQiwei().getKey());
    }

    /**
     * 创建钉钉机器人发送器 Bean
     *
     * @param properties 监控配置属性
     * @return 钉钉机器人发送器实例
     */
    @Bean
    public DingDingRobotSender dingDingRobotSender(BaseMonitorProperties properties) {
        return new DingDingRobotSender(properties.getLog().getDingding().getToken(), properties.getLog().getDingding().getSecret());
    }

}
