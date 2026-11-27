/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package io.ddd4j.dropwizard;

import ch.qos.logback.access.common.PatternLayout;
import ch.qos.logback.classic.LoggerContext;
import io.dropwizard.request.logging.layout.SafeRequestParameterConverter;

import java.util.TimeZone;

/**
 * 保留 Dropwizard 5 默认访问日志格式，通过实例转换器适配 Logback 1.6。
 * 上游 LogbackAccessRequestLayout 静态初始化依赖已删除的 classic.DEFAULT_CONVERTER_MAP，
 * 无法通过继承该布局修复，故直接复用 Logback Access 布局和 Dropwizard 的安全转换器。
 */
final class Ddd4jLogbackAccessRequestLayout extends PatternLayout {

    /**
     * 装配 Dropwizard 请求日志的布局和时间格式。
     *
     * @param context  当前框架传入的执行或容器上下文
     * @param timeZone 格式化访问时间使用的时区
     */
    Ddd4jLogbackAccessRequestLayout(LoggerContext context, TimeZone timeZone) {
        setOutputPatternAsHeader(false);
        getInstanceConverterMap().put("requestParameter", SafeRequestParameterConverter::new);
        getInstanceConverterMap().put("reqParameter", SafeRequestParameterConverter::new);
        setPattern("%h %l %u [%t{dd/MMM/yyyy:HH:mm:ss Z," + timeZone.getID()
                + "}] \"%r\" %s %b \"%i{Referer}\" \"%i{User-Agent}\" %D");
        setContext(context);
    }
}
