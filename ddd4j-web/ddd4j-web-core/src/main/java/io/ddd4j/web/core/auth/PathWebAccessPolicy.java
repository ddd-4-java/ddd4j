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

    private static final String PREFIX_PATTERN = "/**";
    private final List<String> disabledPatterns;
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

    private boolean matches(String path, String pattern) {
        if (pattern.endsWith(PREFIX_PATTERN)) {
            String prefix = pattern.substring(0, pattern.length() - PREFIX_PATTERN.length());
            return path.equals(prefix) || path.startsWith(prefix + '/');
        }
        return path.equals(pattern);
    }
}
