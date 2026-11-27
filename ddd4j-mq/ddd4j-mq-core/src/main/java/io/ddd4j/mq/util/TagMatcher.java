package io.ddd4j.mq.util;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * MQ tag 表达式匹配器。
 *
 * <p>兼容旧库 {@code MQFilter} 的核心语义，支持 {@code *}、{@code A || B}、{@code -C}。
 * 表达式中的 {@code ||} 可以带空格，也可以写成 {@code A||B}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class TagMatcher {

    /** \u6807\u7b7e\u8868\u8fbe\u5f0f\u4e2d\u7684\u6216\u5206\u9694\u7b26\u3002 */
    private static final String OR = "||";
    /** \u53ef\u5339\u914d\u4efb\u610f\u6807\u7b7e\u7684\u8ba2\u9605\u6807\u8bb0\u3002 */
    private static final String WILDCARD = "*";

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>TagMatcher</code> \u5b9e\u4f8b\u3002 */
    private TagMatcher() {
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

        if (Objects.nonNull(candidate) && excludes.contains(candidate)) {
            return false;
        }
        if (wildcard) {
            return true;
        }
        if (includes.isEmpty()) {
            return Objects.isNull(candidate) || !excludes.contains(candidate);
        }
        return Objects.nonNull(candidate) && includes.contains(candidate);
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

    /**
     * \u5c06\u6807\u7b7e\u8868\u8fbe\u5f0f\u62c6\u6210\u53ef\u7528\u4e8e\u5339\u914d\u7684\u6807\u7b7e\u96c6\u5408\u3002
     * @param expression \u4ee3\u7406\u8ba2\u9605\u4f7f\u7528\u7684\u6807\u7b7e\u8868\u8fbe\u5f0f
     * @return \u89e3\u6790\u51fa\u7684\u6807\u7b7e\uff0c\u7a7a\u767d\u6216\u5168\u5339\u914d\u8868\u8fbe\u5f0f\u8fd4\u56de\u7a7a\u5217\u8868
     */
    private static List<String> tokens(String expression) {
        if (Objects.isNull(expression) || io.ddd4j.kit.lang.StrKit.isBlank(expression) || Objects.equals(WILDCARD, expression.trim())) {
            return List.of();
        }
        return Stream.of(expression.replace(OR, " ").trim().split("\\s+"))
                .map(String::trim)
                .filter(token -> !io.ddd4j.kit.lang.StrKit.isEmpty(token))
                .filter(token -> !OR.equals(token))
                .toList();
    }

    /**
     * \u6e05\u9664\u6807\u7b7e\u7684\u9996\u5c3e\u7a7a\u767d\uff0c\u628a\u7a7a\u767d\u503c\u5f52\u4e00\u4e3a\u7a7a\u5f15\u7528\u3002
     * @param s \u5f85\u5f52\u4e00\u5316\u7684\u6807\u7b7e\u6587\u672c
     * @return \u53bb\u6389\u9996\u5c3e\u7a7a\u767d\u7684\u6807\u7b7e\uff0c\u7a7a\u767d\u503c\u8fd4\u56de\u7a7a
     */
    private static String trimToNull(String s) {
        if (Objects.isNull(s) || io.ddd4j.kit.lang.StrKit.isBlank(s)) {
            return null;
        }
        return s.trim();
    }
}
