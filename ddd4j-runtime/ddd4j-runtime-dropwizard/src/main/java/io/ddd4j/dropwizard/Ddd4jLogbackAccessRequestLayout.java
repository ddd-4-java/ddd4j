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
     * \u88c5\u914d Dropwizard \u8bf7\u6c42\u65e5\u5fd7\u7684\u5e03\u5c40\u548c\u65f6\u95f4\u683c\u5f0f\u3002
     * @param context \u5f53\u524d\u6846\u67b6\u4f20\u5165\u7684\u6267\u884c\u6216\u5bb9\u5668\u4e0a\u4e0b\u6587
     * @param timeZone \u683c\u5f0f\u5316\u8bbf\u95ee\u65f6\u95f4\u4f7f\u7528\u7684\u65f6\u533a
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
