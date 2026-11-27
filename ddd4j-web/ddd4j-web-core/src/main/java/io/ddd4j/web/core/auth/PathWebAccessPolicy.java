package io.ddd4j.web.core.auth;

import io.ddd4j.kit.lang.StrKit;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import io.ddd4j.web.core.context.WebRequestContext;

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
     * \u521b\u5efa\u57fa\u4e8e\u8def\u5f84\u6a21\u5f0f\u7684\u8bbf\u95ee\u7b56\u7565\u3002
     * @param disabledPatterns \u514d\u8ba4\u8bc1\u8def\u5f84\u6a21\u5f0f
     * @param defaultMode \u5176\u4ed6\u8def\u5f84\u7684\u9ed8\u8ba4\u8ba4\u8bc1\u6a21\u5f0f
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
     * \u6309\u8bf7\u6c42\u8def\u5f84\u9009\u62e9\u8ba4\u8bc1\u6a21\u5f0f\u3002
     * @param context \u5f53\u524d\u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @return \u8def\u5f84\u5339\u914d\u65f6\u4e3a\u514d\u8ba4\u8bc1\u6a21\u5f0f\uff0c\u5426\u5219\u4e3a\u9ed8\u8ba4\u8ba4\u8bc1\u6a21\u5f0f
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
