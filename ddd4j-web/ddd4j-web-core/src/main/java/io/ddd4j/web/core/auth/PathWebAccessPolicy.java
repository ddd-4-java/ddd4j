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
package io.ddd4j.web.core.auth;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.web.core.context.WebRequestContext;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 基于精确路径和 {@code /**} 前缀模式的访问策略。
 */
public final class PathWebAccessPolicy implements WebAccessPolicy {

    /** \u7528\u4e8e\u8bc6\u522b\u524d\u7f00\u5f0f\u8def\u5f84\u89c4\u5219\u7684\u6a21\u5f0f\u3002 */
    private static final String PREFIX_PATTERN = "/**";
    /** \u8131\u79bb\u9ed8\u8ba4\u8ba4\u8bc1\u8981\u6c42\u7684\u8bf7\u6c42\u8def\u5f84\u6a21\u5f0f\u3002 */
    private final List<String> disabledPatterns;
    /** \u5f53\u8bf7\u6c42\u6ca1\u6709\u5339\u914d\u663e\u5f0f\u8bbf\u95ee\u7b56\u7565\u65f6\u4f7f\u7528\u7684\u8ba4\u8bc1\u6a21\u5f0f\u3002 */
    private final AuthenticationMode defaultMode;

    /**
     * 创建基于路径模式的访问策略。
     *
     * @param disabledPatterns 免认证路径模式
     * @param defaultMode 其他路径的默认认证模式
     */
    public PathWebAccessPolicy(Collection<String> disabledPatterns, AuthenticationMode defaultMode) {
        Objects.requireNonNull(disabledPatterns, "disabledPatterns must not be null");
        this.disabledPatterns = disabledPatterns.stream()
                .filter(StrKit::isNotBlank)
                .map(String::trim)
                .distinct()
                .toList();
        this.defaultMode = Objects.requireNonNull(defaultMode, "defaultMode must not be null");
    }

    /**
     * 按请求路径选择认证模式。
     *
     * @param context 当前请求上下文
     * @return 路径匹配时为免认证模式，否则为默认认证模式
     */
    @Override
    public AuthenticationMode authenticationMode(WebRequestContext context) {
        String path = Objects.requireNonNull(context, "context must not be null").path();
        return disabledPatterns.stream().anyMatch(pattern -> matches(path, pattern))
                ? AuthenticationMode.DISABLED : defaultMode;
    }

    /**
     * \u4f9d\u636e\u663e\u5f0f\u8def\u5f84\u548c\u901a\u914d\u89c4\u5219\u68c0\u67e5\u8bf7\u6c42\u662f\u5426\u547d\u4e2d\u914d\u7f6e\u3002
     * @param path \u5f53\u524d\u88ab\u5904\u7406\u7684 HTTP \u8bf7\u6c42\u8def\u5f84
     * @param pattern \u5f53\u524d\u8981\u5339\u914d\u7684\u8bbf\u95ee\u7b56\u7565\u8def\u5f84\u89c4\u5219
     * @return \u8bf7\u6c42\u8def\u5f84\u7b26\u5408\u5f53\u524d\u8bbf\u95ee\u8def\u5f84\u89c4\u5219\u65f6\u8fd4\u56de true
     */
    private boolean matches(String path, String pattern) {
        if (pattern.endsWith(PREFIX_PATTERN)) {
            String prefix = pattern.substring(0, pattern.length() - PREFIX_PATTERN.length());
            return path.equals(prefix) || path.startsWith(prefix + '/');
        }
        return path.equals(pattern);
    }
}
