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

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * {@link LocalTime} 类型格式化器。
 * <p>用于 Spring MVC 参数绑定中 {@link LocalTime} 类型的字符串解析与打印。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class LocalTimeFormatter implements Formatter<LocalTime> {
    /**
     * 时间格式化器
     */
    public final DateTimeFormatter FORMATTER;

    /**
     * 构造指定格式的 LocalTime 格式化器。
     *
     * @param pattern 时间格式模式，如 "HH:mm:ss"
     */
    public LocalTimeFormatter(String pattern) {
        FORMATTER = DateTimeFormatter.ofPattern(pattern, Locale.CHINESE);
    }

    /**
     * \u6309\u5f53\u524d Spring \u914d\u7f6e\u89e3\u6790\u65f6\u523b\u6587\u672c\u3002
     * @param text \u5f85\u89e3\u6790\u7684\u65e5\u671f\u6216\u65f6\u95f4\u6587\u672c
     * @param locale \u5f53\u524d\u683c\u5f0f\u5316\u8bf7\u6c42\u7684 Locale
     * @return \u89e3\u6790\u540e\u7684\u672c\u5730\u65f6\u523b
     */
    @Override
    public LocalTime parse(String text, Locale locale) {
        return LocalTime.parse(text, FORMATTER);
    }

    /**
     * \u6309\u5f53\u524d Spring \u914d\u7f6e\u683c\u5f0f\u5316\u65f6\u523b\u3002
     * @param object \u5f85\u5199\u5165\u6216\u683c\u5f0f\u5316\u7684 Java \u5bf9\u8c61
     * @param locale \u5f53\u524d\u683c\u5f0f\u5316\u8bf7\u6c42\u7684 Locale
     * @return \u683c\u5f0f\u5316\u540e\u7684\u65f6\u523b\u5b57\u7b26\u4e32
     */
    @Override
    public String print(LocalTime object, Locale locale) {
        return FORMATTER.format(object);
    }

}
