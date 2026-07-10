package io.ddd4j.extension.monitor.domain.robot.service;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.EvaluatorFilter;
import ch.qos.logback.core.spi.FilterReply;
import io.ddd4j.extension.monitor.application.service.Sender;
import io.ddd4j.extension.monitor.domain.robot.model.RobotAppender;
import io.ddd4j.extension.monitor.infras.config.BaseMonitorProperties;
import io.ddd4j.extension.monitor.infras.utils.IpUtils;
import io.ddd4j.kit.lang.StrKit;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import static ch.qos.logback.core.AsyncAppenderBase.DEFAULT_MAX_FLUSH_TIME;

/**
 * Logback 日志告警追加服务
 *
 * <p>负责将 Logback 日志通过异步 Appender 发送到钉钉/企微机器人。
 * 支持日志级别过滤、关键字匹配、日志名称排除等过滤策略。
 *
 * <p>本类为纯 Java 实现，不再依赖 Spring 容器；上层框架在完成依赖装配后调用 {@link #init()} 即可触发装配，
 * 调用 {@link #stop()} 可释放资源。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j(topic = "### BASE-MONITOR : RobotLogbackAppendService ###")
public class RobotLogbackAppendService {
    /**
     * Logback 日志上下文
     */
    private LoggerContext loggerContext = null;
    /**
     * 监控配置属性（由上层框架注入；可通过 {@link #setBaseMonitorProperties(BaseMonitorProperties)} 设置）
     */
    @Setter
    private BaseMonitorProperties baseMonitorProperties;
    /**
     * 消息发送器（由上层框架注入；可通过 {@link #setSender(Sender)} 设置）
     */
    @Setter
    private Sender sender;
    /**
     * 应用名称（用于日志告警中展示，当配置未指定应用名称时使用）
     */
    @Setter
    private String appName;
    /**
     * 已启动的异步 Appender，用于停止时释放资源
     */
    private AsyncAppender asyncAppender;
    /**
     * 已启动的机器人 Appender，用于停止时释放资源
     */
    private RobotAppender robotAppender;

    public RobotLogbackAppendService() {
    }

    public RobotLogbackAppendService(BaseMonitorProperties baseMonitorProperties) {
        this.baseMonitorProperties = baseMonitorProperties;
    }

    public RobotLogbackAppendService(BaseMonitorProperties baseMonitorProperties, Sender sender, String appName) {
        this.baseMonitorProperties = baseMonitorProperties;
        this.sender = sender;
        this.appName = appName;
    }

    /**
     * 判断集合是否非空（替代 {@code org.springframework.util.CollectionUtils.isEmpty}）。
     *
     * @param collection 待判断的集合
     * @return 集合为 null 或空时返回 false，否则返回 true
     */
    private static boolean isNotEmpty(java.util.Collection<?> collection) {
        return collection != null && !collection.isEmpty();
    }

    /**
     * 初始化日志告警 Appender，等价于原先 {@code @PostConstruct} 的行为。
     */
    public void init() {
        this.initLoggerContext();
        loggerContext.putProperty("ip", IpUtils.getLocalAddress());
        if (Objects.nonNull(baseMonitorProperties.getLog().getApp()) && StrKit.isNotBlank(baseMonitorProperties.getLog().getApp().getProject())) {
            loggerContext.putProperty("project", baseMonitorProperties.getLog().getApp().getProject());
        }
        this.robotAppender = this.robotAppender();
        this.asyncAppender = this.asyncAppender();
        this.asyncAppender.start();
        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(asyncAppender);
    }

    /**
     * 停止并释放 Appender 资源，等价于原先 {@code @Bean(destroyMethod = "stop")} 的行为。
     */
    public void stop() {
        if (Objects.nonNull(asyncAppender)) {
            asyncAppender.stop();
        }
        if (Objects.nonNull(robotAppender)) {
            robotAppender.stop();
        }
    }

    public RobotAppender robotAppender() {
        return RobotAppender.build(loggerContext, baseMonitorProperties, sender, appName);
    }

    // 构建 异步的 AsyncAppender 包装RobotAppend
    public AsyncAppender asyncAppender() {
        AsyncAppender asyncAppender = new AsyncAppender();
        BaseMonitorProperties.Log.Config config = baseMonitorProperties.getLog().getConfig();
        asyncAppender.setContext(loggerContext);
        // http://logback.qos.ch/manual/appenders.html#AsyncAppender
        // 提取调用方数据可能相当昂贵。
        // 若要提高性能，默认情况下，当事件添加到事件队列时，不会提取与事件关联的调用方数据。
        // 默认情况下，只有“廉价”数据，如线程名和 都被复制了。
        asyncAppender.setIncludeCallerData(config.getAsyncAppenderIncludeCallerData());

        //blockingQueue长度决定了队列能放多少信息，在默认的配置下，如果blockingQueue放满了，后续想要输出日志的线程会被阻塞，直到Worker线程处理掉队列中的信息为止。
        // 根据实际情况适当调整队列长度，可以防止线程被阻塞。
        asyncAppender.setQueueSize(config.getAsyncAppenderQueueSize());

        // 默认情况下，当阻塞队列剩余20% 的容量时，它将删除 TRACE、 DEBUG 和 INFO 级别的事件，只保留 WARN 和 ERROR 级别的事件。
        // super.setDiscardingThreshold();

        //如果配置neverBlock=true，当队列满了之后，后面阻塞的线程想要输出的消息就直接被丢弃，从而线程不会阻塞。
        // 这个配置用于线程很重要，不能卡顿，而且日志又不是很重要的场景，因为很有可能会丢日志
        asyncAppender.setNeverBlock(config.getAsyncAppenderNeverBlock());

        //Depending on the queue depth and latency to the referenced appender,
        // the AsyncAppender may take an unacceptable amount of time to fully flush the queue.
        // When the LoggerContext is stopped,
        // the AsyncAppender stop method waits up to this timeout for the worker thread to complete.
        // Use maxFlushTime to specify a maximum queue flush timeout in milliseconds.
        // Events that cannot be processed within this window are discarded.
        // Semantics of this value are identical to that of Thread.join(long).
        asyncAppender.setMaxFlushTime(DEFAULT_MAX_FLUSH_TIME);

        asyncAppender.setName("robotAsyncAppender");

        RobotAppender robotAppender = this.robotAppender();
        asyncAppender.addAppender(robotAppender);
        ThresholdFilter thresholdFilter = this.buildThresholdFilter();
        asyncAppender.addFilter(thresholdFilter);
        EvaluatorFilter<ILoggingEvent> ignoresFilter = this.ignores();
        if (Objects.nonNull(ignoresFilter)) {
            asyncAppender.addFilter(ignoresFilter);
        }
        EvaluatorFilter<ILoggingEvent> ignoreLogNamesFilter = this.ignoreLogNames();
        if (Objects.nonNull(ignoreLogNamesFilter)) {
            asyncAppender.addFilter(ignoreLogNamesFilter);
        }
        EvaluatorFilter<ILoggingEvent> includesFilter = this.includes();
        if (Objects.nonNull(includesFilter)) {
            asyncAppender.addFilter(includesFilter);
        }
        EvaluatorFilter<ILoggingEvent> keywordExpressionFilter = this.keywordExpression();
        if (Objects.nonNull(keywordExpressionFilter)) {
            asyncAppender.addFilter(keywordExpressionFilter);
        }
        this.addLoggerNameRobotAppender(asyncAppender);
        return asyncAppender;
    }

    // 添加 logger name 到 robot append
    private void addLoggerNameRobotAppender(AsyncAppender asyncAppender) {
        BaseMonitorProperties.Log.Config config = baseMonitorProperties.getLog().getConfig();
        for (String loggerName : config.getAppendLoggerNames()) {
            Logger logger = loggerContext.getLogger(loggerName);
            if (Objects.isNull(logger)) {
                log.warn("alarm logger name ={} not found", loggerName);
                continue;
            }
            logger.addAppender(asyncAppender);
        }
    }

    // 初始化 Logback 日志上下文。
    private void initLoggerContext() {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (!(factory instanceof LoggerContext)) {
            throw new IllegalArgumentException("LoggerFactory is not a Logback LoggerContext");
        }
        this.loggerContext = (LoggerContext) factory;
    }

    // 构建表达式 过滤器
    private EvaluatorFilter<ILoggingEvent> keywordExpression() {
        BaseMonitorProperties.Log.Config config = baseMonitorProperties.getLog().getConfig();
        if (StrKit.isNotBlank(config.getKeywordExpression())) {
            return getEvaluatorFilter(config.getKeywordExpression(), FilterReply.ACCEPT, FilterReply.DENY);
        }
        return null;
    }

    // 包含关键字
    private EvaluatorFilter<ILoggingEvent> includes() {
        BaseMonitorProperties.Log.Config config = baseMonitorProperties.getLog().getConfig();
        if (isNotEmpty(config.getIncludes())) {
            StringBuilder builder = new StringBuilder("return ");
            for (int index = 0; index < config.getIncludes().size(); index++) {
                String keyword = config.getIncludes().get(index);
                if (index != 0 && index != config.getIncludes().size()) {
                    builder.append(" || ");
                }
                builder.append(" formattedMessage.contains(\"").append(keyword).append("\")");

            }
            builder.append(";");
            return getEvaluatorFilter(builder.toString(), FilterReply.ACCEPT, FilterReply.DENY);
        }
        return null;
    }

    // 排除 关键 loggerName 的告警
    private EvaluatorFilter<ILoggingEvent> ignoreLogNames() {
        BaseMonitorProperties.Log.Config config = baseMonitorProperties.getLog().getConfig();
        if (isNotEmpty(config.getIgnoreLogNames())) {
            StringBuilder builder = new StringBuilder("return ");
            for (int index = 0; index < config.getIgnoreLogNames().size(); index++) {
                String keyword = config.getIgnoreLogNames().get(index);
                if (index != 0 && index != config.getIgnoreLogNames().size()) {
                    builder.append(" || ");
                }
                builder.append(" logger.contains(\"").append(keyword).append("\")");
            }
            builder.append(";");
            return getEvaluatorFilter(builder.toString(), FilterReply.DENY, FilterReply.NEUTRAL);
        }
        return null;
    }

    // 排除 关键 信息的告警
    private EvaluatorFilter<ILoggingEvent> ignores() {
        BaseMonitorProperties.Log.Config config = baseMonitorProperties.getLog().getConfig();
        if (isNotEmpty(config.getIgnores())) {
            StringBuilder builder = new StringBuilder("return ");
            for (int index = 0; index < config.getIgnores().size(); index++) {
                String keyword = config.getIgnores().get(index);
                if (index != 0 && index != config.getIgnores().size()) {
                    builder.append(" || ");
                }
                builder.append(" formattedMessage.contains(\"").append(keyword).append("\")");
            }
            builder.append(";");
            return getEvaluatorFilter(builder.toString(), FilterReply.DENY, FilterReply.NEUTRAL);
        }
        return null;
    }

    /**
     * 构建表达式过滤器。
     *
     * <p>注意：logback 1.5.x 移除了 {@code JaninoEventEvaluator}，
     * 此方法暂时返回 null 禁用 Janino 表达式过滤。
     * 后续可改用 logback 1.5.x 的 {@code IfEvaluator} 或自定义 evaluator。
     *
     * @see <a href="http://logback.qos.ch/manual/filters.html#EvaluatorFilter">EvaluatorFilter</a>
     */
    private EvaluatorFilter<ILoggingEvent> getEvaluatorFilter(String expression, FilterReply onMatch, FilterReply onMismatch) {
        // logback 1.5.x 不再提供 JaninoEventEvaluator，暂时禁用表达式过滤
        log.warn("Janino 表达式过滤在 logback 1.5.x 中不可用，已禁用: {}", expression);
        return null;
    }

    /**
     * 构建拦截器 伐值以上的日志都会打印
     *
     * @see <a href="http://logback.qos.ch/manual/filters.html#ThresholdFilter">logback</a>
     */
    private ThresholdFilter buildThresholdFilter() {
        BaseMonitorProperties.Log.Config config = baseMonitorProperties.getLog().getConfig();
        ThresholdFilter thresholdFilter = new ThresholdFilter();
        thresholdFilter.setLevel(config.getLogLevel());
        thresholdFilter.start();
        return thresholdFilter;
    }


}
