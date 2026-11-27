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

    /**
     * 标签表达式中的或分隔符。
     */
    private static final String OR = "||";
    /**
     * 可匹配任意标签的订阅标记。
     */
    private static final String WILDCARD = "*";

    /**
     * 限制调用方直接创建 <code>TagMatcher</code> 实例。
     */
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
     * 将标签表达式拆成可用于匹配的标签集合。
     *
     * @param expression 代理订阅使用的标签表达式
     * @return 解析出的标签，空白或全匹配表达式返回空列表
     */
    private static List<String> tokens(String expression) {
        if (Objects.isNull(expression) || io.ddd4j.kit.lang.StrKit.isBlank(expression) || Objects.equals(WILDCARD, expression.trim())) {
            return java.util.Collections.emptyList();
        }
        return Stream.of(expression.replace(OR, " ").trim().split("\\s+"))
                .map(String::trim)
                .filter(token -> !io.ddd4j.kit.lang.StrKit.isEmpty(token))
                .filter(token -> !OR.equals(token))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 清除标签的首尾空白，把空白值归一为空引用。
     *
     * @param s 待归一化的标签文本
     * @return 去掉首尾空白的标签，空白值返回空
     */
    private static String trimToNull(String s) {
        if (Objects.isNull(s) || io.ddd4j.kit.lang.StrKit.isBlank(s)) {
            return null;
        }
        return s.trim();
    }
}
