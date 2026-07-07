package io.ddd4j.extension.monitor.domain.robot.model;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import com.google.common.util.concurrent.RateLimiter;
import io.ddd4j.extension.monitor.application.service.Sender;
import io.ddd4j.extension.monitor.infras.config.BaseMonitorProperties;
import io.ddd4j.kit.lang.StrKit;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * 实现异步 日志队列
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Slf4j(topic = "### BASE-MONITOR : RobotAppender ###")
public class RobotAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
    /**
     * 消息发送计数统计
     */
    private static Map<String, Integer> msgCount = new ConcurrentHashMap<>(3);
    /**
     * 最近发送的消息内容
     */
    private static String LATEST_MSG = "";
    /**
     * 最近消息的连续发送次数
     */
    private static AtomicInteger LATEST_MSG_COUNT = new AtomicInteger(0);

    /**
     * 消息发送速率限制器，每个机器人每分钟最多发送20条
     */
    private static RateLimiter rateLimiter;

    /**
     * 发送速率限制 [每分钟最多20次] 1/3.5~= 0.2857
     */
    private Double rateLimiterPermitsPerSecond = 0.2857;

    /**
     * 监控配置属性（由构建方注入，替代原先通过 SpringContext 获取）
     */
    private BaseMonitorProperties properties;
    /**
     * 消息发送器（由构建方注入，替代原先通过 SpringContext 获取）
     */
    private Sender sender;

    /**
     * 定义 layout 处理器 Encoder，用于格式化日志输出
     *
     * @see PatternLayoutEncoder
     * @see LayoutWrappingEncoder
     * @see <a href="http://logback.qos.ch/manual/encoders.html">Logback Encoder 文档</a>
     */
    private Encoder<ILoggingEvent> encoder;

    public RobotAppender() {
        super();
        super.setName("dRobot");
    }

    /**
     * 构建 RobotAppender 实例
     *
     * @param loggerContext Logback 日志上下文
     * @param properties    监控配置属性
     * @param sender        消息发送器
     * @param appName       应用名称（当配置中未指定时使用）
     * @return RobotAppender 实例
     */
    public static RobotAppender build(LoggerContext loggerContext, BaseMonitorProperties properties, Sender sender, String appName) {
        RobotAppender robotAppender = new RobotAppender();
        robotAppender.setProperties(properties);
        robotAppender.setSender(sender);
        robotAppender.setRateLimiterPermitsPerSecond(properties.getLog().getRateLimiterPermitsPerSecond());
        robotAppender.setContext(loggerContext);
        RobotLayout layout = buildRobotLayout(loggerContext, properties, appName);
        layout.setMdcList(properties.getLog().getConfig().getMdcList());
        layout.start();
        robotAppender.setLayout(layout);
        robotAppender.start();
        return robotAppender;
    }

    // 构建RobotLayout
    private static RobotLayout buildRobotLayout(LoggerContext loggerContext, BaseMonitorProperties properties, String appName) {
        RobotLayout layout = new RobotLayout();
        layout.setContext(loggerContext);
        layout.setProperties(properties);
        BaseMonitorProperties.Log.App applicationConfig = properties.getLog().getApp();
        if (Objects.isNull(applicationConfig)) {
            applicationConfig = new BaseMonitorProperties.Log.App();
        }
        String app = applicationConfig.getName();
        if (!StrKit.isNotBlank(app)) {
            app = appName;
        }
        layout.setApp(app);
        String project = applicationConfig.getProject();
        if (StrKit.isNotBlank(project)) {
            layout.setProject(project);
        }
        return layout;
    }

    private static synchronized void acquire(BaseMonitorProperties properties) {
        if (Objects.isNull(rateLimiter)) {
            rateLimiter = RateLimiter.create(properties.getLog().getRateLimiterPermitsPerSecond());
        }
        rateLimiter.acquire();
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (Objects.isNull(encoder)) {
            addWarn("encoder is null");
            return;
        }
        byte[] encodeBytes = encoder.encode(eventObject);
        try {
            acquire(properties);
            String msg = new String(encodeBytes, StandardCharsets.UTF_8);
            if (!Objects.equals(LATEST_MSG, msg)) {
                LATEST_MSG = msg;
                LATEST_MSG_COUNT.set(1);
            }
            if (LATEST_MSG_COUNT.incrementAndGet() < 3) {
                // 连续发送同一条消息小于3次
                sender.send(msg);
            }
        } catch (Exception e) {
            log.error("send robot error", e);
        }
    }

    // 设置 layout
    public void setLayout(Layout<ILoggingEvent> layout) {
        LayoutWrappingEncoder<ILoggingEvent> customLayoutEncoder = new LayoutWrappingEncoder<>();
        customLayoutEncoder.setLayout(layout);
        customLayoutEncoder.setContext(context);
        this.encoder = customLayoutEncoder;
    }
}
