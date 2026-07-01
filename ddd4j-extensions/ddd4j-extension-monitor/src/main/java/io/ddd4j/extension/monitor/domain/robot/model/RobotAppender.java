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
import io.ddd4j.spring.context.SpringContext;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

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
    private static Map<String, Integer> msgCount = new ConcurrentHashMap<>(3);
    private static String LATEST_MSG = "";
    private static AtomicInteger LATEST_MSG_COUNT = new AtomicInteger(0);

    // 每个机器人每分钟最多发送20条
    private static RateLimiter rateLimiter;

    // 发送速率 [每分钟最多20次] 1/3.5~= 0.2857
    private Double rateLimiterPermitsPerSecond = 0.2857;

    /**
     * 定义 layout 处理器 Encode
     *
     * @see PatternLayoutEncoder
     * @see LayoutWrappingEncoder
     * {@see http://logback.qos.ch/manual/encoders.html}
     */
    private Encoder<ILoggingEvent> encoder;

    public RobotAppender() {
        super();
        super.setName("dRobot");
    }

    public static RobotAppender build(LoggerContext loggerContext) {
        RobotAppender robotAppender = new RobotAppender();
        robotAppender.setRateLimiterPermitsPerSecond(SpringContext.getBean(BaseMonitorProperties.class).getLog().getRateLimiterPermitsPerSecond());
        robotAppender.setContext(loggerContext);
        RobotLayout layout = buildRobotLayout(loggerContext);
        layout.setMdcList(SpringContext.getBean(BaseMonitorProperties.class).getLog().getConfig().getMdcList());
        layout.start();
        robotAppender.setLayout(layout);
        robotAppender.start();
        return robotAppender;
    }

    // 构建RobotLayout
    private static RobotLayout buildRobotLayout(LoggerContext loggerContext) {
        RobotLayout layout = new RobotLayout();
        layout.setContext(loggerContext);
        BaseMonitorProperties.Log.App applicationConfig = SpringContext.getBean(BaseMonitorProperties.class).getLog().getApp();
        if (Objects.isNull(applicationConfig)) {
            applicationConfig = new BaseMonitorProperties.Log.App();
        }
        String app = applicationConfig.getName();
        if (!StringUtils.hasText(app)) {
            app = SpringContext.getEnv().getProperty("spring.application.name");
        }
        layout.setApp(app);
        String project = applicationConfig.getProject();
        if (StringUtils.hasText(project)) {
            layout.setProject(project);
        }
        return layout;
    }

    private static synchronized void acquire() {
        if (Objects.isNull(rateLimiter)) {
            BaseMonitorProperties properties = SpringContext.getBean(BaseMonitorProperties.class);
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
            acquire();
            String msg = new String(encodeBytes, StandardCharsets.UTF_8);
            if (!Objects.equals(LATEST_MSG, msg)) {
                LATEST_MSG = msg;
                LATEST_MSG_COUNT.set(1);
            }
            if (LATEST_MSG_COUNT.incrementAndGet() < 3) {
                // 连续发送同一条消息小于3次
                SpringContext.getBean(Sender.class).send(msg);
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
