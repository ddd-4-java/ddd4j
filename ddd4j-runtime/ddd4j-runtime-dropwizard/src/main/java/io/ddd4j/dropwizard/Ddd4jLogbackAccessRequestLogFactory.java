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
