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
package io.ddd4j.core.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * 国际化提供者接口（策略模式）。
 * <p>
 * 各框架适配层提供实现：
 * <ul>
 *   <li>Spring: 基于 MessageSource</li>
 *   <li>Guice: 基于 ResourceBundle</li>
 * </ul>
 * 默认实现返回原始 key。
 *
 * <p>1.0.x（JDK8）实现说明：3.0.x 版本接口内使用 private static 方法（Java 9+），
 * 此处降级为接口级 static 方法，语义保持一致。
 *
 * <h3>占位符约定</h3>
 * <ul>
 *   <li><b>资源文件中</b>：使用 {@code java.text.MessageFormat} 风格 {@code {0}, {1}, ...}
 *       （与 Spring MessageSource 一致）</li>
 *   <li><b>纯文本 message（未命中资源）</b>：兼容 SLF4J 风格 {@code {}}
 *       按顺序替换，便于业务代码直接写 {@code "Repository for {} not found"}</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface I18nProvider {

    /**
     * 默认实现：使用 Java ResourceBundle 加载 i18n/messages.properties。
     * <p>
     * 命中资源时走 {@link MessageFormat}（{@code {0}} 风格）；未命中资源时走 SLF4J 风格
     * （{@code {}} 按出现顺序替换），避免业务消息中的 {@code {}} 被 MessageFormat 误解析。
     */
    I18nProvider DEFAULT = (key, args) -> {
        if (isBlank(key)) {
            return null;
        }
        // 1. 先尝试从 i18n 资源加载（资源文件使用 {0} 风格）
        String pattern = null;
        try {
            ResourceBundle bundle = ResourceBundle.getBundle("i18n/messages", Locale.getDefault());
            pattern = bundle.getString(key);
        } catch (MissingResourceException e) {
            // 找不到资源文件或 key，pattern 保持 null
        }
        // 2. 命中资源：走 MessageFormat（{0} 风格）
        if (Objects.nonNull(pattern)) {
            if (Objects.isNull(args) || args.length == 0) {
                return pattern;
            }
            return MessageFormat.format(pattern, args);
        }
        // 3. 未命中资源：把原始 key 当作纯文本，按 SLF4J {} 风格按序替换
        return formatSlfStyle(key, args);
    };

    /**
     * 按 SLF4J 风格 {@code {}} 占位符按出现顺序替换参数。
     * <p>当 args 为空或 null 时原样返回 message。
     *
     * @param message 原始消息（可能含 {@code {}} 占位符）
     * @param args    替换参数
     * @return 替换后的消息
     */
    static String formatSlfStyle(String message, Object... args) {
        if (Objects.isNull(args) || args.length == 0) {
            return message;
        }
        String result = message;
        for (Object arg : args) {
            int idx = result.indexOf("{}");
            if (idx < 0) {
                break;
            }
            result = result.substring(0, idx) + Objects.toString(arg, "null") + result.substring(idx + 2);
        }
        return result;
    }

    /**
     * 判断字符串是否为空白（null、空串或全空白字符，等价 3.0.x StrKit.isBlank）。
     */
    static boolean isBlank(String text) {
        return Objects.isNull(text) || text.trim().isEmpty();
    }

    /**
     * 获取国际化消息
     *
     * @param key  消息 key
     * @param args 格式化参数
     * @return 国际化后的消息
     */
    String getMessage(String key, Object... args);
}
