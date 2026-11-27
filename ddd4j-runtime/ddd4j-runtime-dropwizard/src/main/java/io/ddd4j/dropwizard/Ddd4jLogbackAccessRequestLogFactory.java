/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package io.ddd4j.dropwizard;

import ch.qos.logback.access.common.spi.IAccessEvent;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.server.AbstractServerFactory;
import io.dropwizard.logging.common.AppenderFactory;
import io.dropwizard.logging.common.async.AsyncAppenderFactory;
import io.dropwizard.logging.common.filter.LevelFilterFactory;
import io.dropwizard.logging.common.filter.NullLevelFilterFactory;
import io.dropwizard.logging.common.layout.LayoutFactory;
import io.dropwizard.request.logging.LogbackAccessRequestLog;
import io.dropwizard.request.logging.LogbackAccessRequestLogFactory;
import io.dropwizard.request.logging.RequestLogFactory;
import io.dropwizard.request.logging.async.AsyncAccessEventAppenderFactory;
import org.eclipse.jetty.server.RequestLog;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 适配 Logback 1.6 的默认访问日志工厂，沿用 Dropwizard 的输出与异步处理组件。
 * 上游 build 将布局工厂写死，覆盖此入口只替换布局，保留原有 Appender 配置。
 */
public final class Ddd4jLogbackAccessRequestLogFactory extends LogbackAccessRequestLogFactory {

    /**
     * 在服务启动前适配默认访问日志，保留自定义工厂、Appender 和禁用日志的配置。
     *
     * @param configuration Dropwizard 应用配置
     */
    public static void configure(Configuration configuration) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        if (configuration.getServerFactory() instanceof AbstractServerFactory serverFactory) {
            RequestLogFactory<?> factory = serverFactory.getRequestLogFactory();
            if (Objects.nonNull(factory) && factory.getClass().equals(LogbackAccessRequestLogFactory.class)) {
                LogbackAccessRequestLogFactory original = (LogbackAccessRequestLogFactory) factory;
                Ddd4jLogbackAccessRequestLogFactory compatible = new Ddd4jLogbackAccessRequestLogFactory();
                compatible.setAppenders(original.getAppenders());
                serverFactory.setRequestLogFactory(compatible);
            }
        }
    }

    /**
     * \u521b\u5efa\u5f53\u524d Dropwizard \u5e94\u7528\u4f7f\u7528\u7684 HTTP \u8bbf\u95ee\u65e5\u5fd7\u3002
     * @param name \u5f53\u524d\u7ec4\u4ef6\u3001\u6307\u6807\u3001\u4efb\u52a1\u6216 Bean \u4f7f\u7528\u7684\u540d\u79f0
     * @return \u53ef\u4e3a\u6846\u67b6\u8bb0\u5f55\u8bbf\u95ee\u4e8b\u4ef6\u7684\u8bf7\u6c42\u65e5\u5fd7
     */
    @Override
    public RequestLog build(String name) {
        Logger logger = (Logger) LoggerFactory.getLogger("http.request");
        logger.setAdditive(false);
        LoggerContext context = logger.getLoggerContext();
        LogbackAccessRequestLog requestLog = new LogbackAccessRequestLog();
        LevelFilterFactory<IAccessEvent> levelFilterFactory = new NullLevelFilterFactory<>();
        AsyncAppenderFactory<IAccessEvent> asyncAppenderFactory = new AsyncAccessEventAppenderFactory();
        LayoutFactory<IAccessEvent> layoutFactory = Ddd4jLogbackAccessRequestLayout::new;
        for (AppenderFactory<IAccessEvent> output : getAppenders()) {
            requestLog.addAppender(output.build(context, name, layoutFactory,
                    levelFilterFactory, asyncAppenderFactory));
        }
        return requestLog;
    }
}
