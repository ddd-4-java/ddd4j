package io.ddd4j.extension.monitor.infras.config;

import io.ddd4j.extension.monitor.application.service.DingDingRobotSender;
import io.ddd4j.extension.monitor.application.service.QiWeiRobotSender;
import io.ddd4j.extension.monitor.domain.robot.service.RobotLogbackAppendService;

/**
 * 日志告警配置（纯 Java 工厂）
 *
 * <p>本类不再依赖 Spring 容器，仅提供各核心对象的工厂方法，由上层框架负责装配与生命周期管理。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class BaseMonitorConfig {

    public BaseMonitorProperties baseMonitorProperties() {
        return new BaseMonitorProperties();
    }

    public RobotLogbackAppendService robotLogbackAppendService() {
        return new RobotLogbackAppendService();
    }

    /**
     * 创建企微机器人发送器
     *
     * @param properties 监控配置属性
     * @return 企微机器人发送器实例
     */
    public QiWeiRobotSender qiWeiRobotSender(BaseMonitorProperties properties) {
        return new QiWeiRobotSender(properties.getLog().getQiwei().getKey());
    }

    /**
     * 创建钉钉机器人发送器
     *
     * @param properties 监控配置属性
     * @return 钉钉机器人发送器实例
     */
    public DingDingRobotSender dingDingRobotSender(BaseMonitorProperties properties) {
        return new DingDingRobotSender(properties.getLog().getDingding().getToken(), properties.getLog().getDingding().getSecret());
    }

}
