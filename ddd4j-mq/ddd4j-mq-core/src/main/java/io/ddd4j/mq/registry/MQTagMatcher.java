package io.ddd4j.mq.registry;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * MQ tag 表达式匹配器。
 *
 * <p>兼容旧库 {@code MQFilter} 的核心语义，支持 {@code *}、{@code A || B}、{@code -C}。
 * 表达式中的 {@code ||} 可以带空格，也可以写成 {@code A||B}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class MQTagMatcher {

    private static final String OR = "||";
    private static final String WILDCARD = "*";

    private MQTagMatcher() {
    }

    /**
     * 判断输入 tag 是否匹配表达式。
     *
     * @param input      待匹配 tag
     * @param expression 表达式，示例：{@code *}、{@code A || B}、{@code -C}
     * @return 是否匹配
     */
    public static boolean match(String input, String expression) {
        List<String> tokens = tokens(expression);
        if (tokens.isEmpty()) {
            return true;
        }

        String candidate = trimToNull(input);
        boolean wildcard = false;
        Set<String> includes = new LinkedHashSet<>();
        Set<String> excludes = new LinkedHashSet<>();

        for (String token : tokens) {
            if (WILDCARD.equals(token)) {
                wildcard = true;
            } else if (token.startsWith("-") && token.length() > 1) {
                excludes.add(token.substring(1).trim());
            } else {
                includes.add(token);
            }
        }

        if (candidate != null && excludes.contains(candidate)) {
            return false;
        }
        if (wildcard) {
            return true;
        }
        if (includes.isEmpty()) {
            return candidate == null || !excludes.contains(candidate);
        }
        return candidate != null && includes.contains(candidate);
    }

    /**
     * 提取表达式中的正向 tag。
     *
     * @param expression 表达式
     * @return 有序正向 tag 集合；通配符和排除项不返回
     */
    public static Set<String> findIncludes(String expression) {
        Set<String> includes = new LinkedHashSet<>();
        for (String token : tokens(expression)) {
            if (WILDCARD.equals(token) || token.startsWith("-")) {
                continue;
            }
            includes.add(token);
        }
        return includes;
    }

    private static List<String> tokens(String expression) {
        if (expression == null || expression.isBlank() || Objects.equals(WILDCARD, expression.trim())) {
            return List.of();
        }
        return List.of(expression.replace(OR, " ").trim().split("\\s+")).stream()
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .filter(token -> !OR.equals(token))
                .toList();
    }

    private static String trimToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }
}
