/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.web.webmvc.utils;

import org.springframework.format.Formatter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * {@link LocalDateTime} 类型格式化器。
 * <p>用于 Spring MVC 参数绑定中 {@link LocalDateTime} 类型的字符串解析与打印。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class LocalDateTimeFormatter implements Formatter<LocalDateTime> {
    /**
     * 日期时间格式化器
     */
    public final DateTimeFormatter FORMATTER;

    /**
     * 构造指定格式的 LocalDateTime 格式化器。
     *
     * @param pattern 日期时间格式模式，如 "yyyy-MM-dd HH:mm:ss"
     */
    public LocalDateTimeFormatter(String pattern) {
        FORMATTER = DateTimeFormatter.ofPattern(pattern, Locale.CHINESE);
    }

    /**
     * 按当前 Spring 配置解析日期时间文本。
     *
     * @param text   待解析的日期或时间文本
     * @param locale 当前格式化请求的 Locale
     * @return 解析后的本地日期时间
     */
    @Override
    public LocalDateTime parse(String text, Locale locale) {
        return LocalDateTime.parse(text, FORMATTER);
    }

    /**
     * 按当前 Spring 配置格式化日期时间。
     *
     * @param object 待写入或格式化的 Java 对象
     * @param locale 当前格式化请求的 Locale
     * @return 格式化后的日期时间字符串
     */
    @Override
    public String print(LocalDateTime object, Locale locale) {
        return FORMATTER.format(object);
    }

}
