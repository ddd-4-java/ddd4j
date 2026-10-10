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
package io.ddd4j.web.core.version;

import java.util.Objects;

/**
 * API 版本值对象：{@code major.minor[.patch]} 三段数值语义的不可变版本号。
 *
 * <p>语义要点：
 * <ul>
 *   <li>数值逐段比较，{@code 2.10.0 &gt; 2.9.0}（非字典序）；</li>
 *   <li>缺失段补 0，{@code 2.1} 与 {@code 2.1.0} 解析结果完全等价；</li>
 *   <li>解析器接受 {@code v2}/{@code 2}/{@code 2.1}/{@code 2.1.0} 等常见书写形态，
 *       其余形态由 {@link #parse(String)} 抛出 {@link IllegalArgumentException} 判非法。</li>
 * </ul>
 *
 * <p>实例创建统一走 {@link #parse(String)} 与 {@link #of(int, int, int)}，创建后不可变、线程安全。
 *
 * @see ApiVersionResolver
 * @see ApiVersionDispatchPolicy
 */
public final class ApiVersion implements Comparable<ApiVersion> {

    /** 主版本号。 */
    private final int major;

    /** 次版本号，书写缺失时为 0。 */
    private final int minor;

    /** 修订号，书写缺失时为 0。 */
    private final int patch;

    /**
     * 私有构造器，仅由 {@link #of(int, int, int)} 内部调用，保证对象不可变。
     *
     * @param major 主版本号
     * @param minor 次版本号
     * @param patch 修订号
     */
    private ApiVersion(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    /**
     * 以三段数值创建版本号。
     *
     * @param major 主版本号
     * @param minor 次版本号
     * @param patch 修订号
     * @return 不可变版本值对象
     * @throws IllegalArgumentException 任一段为负数时抛出
     */
    public static ApiVersion of(int major, int minor, int patch) {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("api version segments must not be negative: "
                    + major + "." + minor + "." + patch);
        }
        return new ApiVersion(major, minor, patch);
    }

    /**
     * 解析常见版本书写形态：{@code v2}、{@code 2}、{@code 2.1}、{@code 2.1.0}。
     *
     * <p>规则：允许前缀 {@code v}/{@code V}；允许 1~3 段十进制非负整数；缺失段补 0；
     * 首尾空白会被裁剪；除此以外的形态（空串、含字母、段数超过 3、负数、溢出等）一律判非法。
     *
     * @param text 待解析的版本文本
     * @return 解析后的版本值对象，缺失段补 0
     * @throws IllegalArgumentException 文本为 null、空白或形态非法时抛出
     */
    public static ApiVersion parse(String text) {
        if (Objects.isNull(text)) {
            throw new IllegalArgumentException("api version text must not be null");
        }
        String normalized = text.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("api version text must not be blank");
        }
        if (normalized.charAt(0) == 'v' || normalized.charAt(0) == 'V') {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("illegal api version: " + text);
        }
        String[] segments = normalized.split("\\.", -1);
        if (segments.length > 3) {
            throw new IllegalArgumentException("illegal api version: " + text);
        }
        int[] values = new int[3];
        for (int i = 0; i < segments.length; i++) {
            values[i] = parseSegment(segments[i], text);
        }
        return new ApiVersion(values[0], values[1], values[2]);
    }

    /**
     * 解析单个版本段，仅接受非空十进制数字序列。
     *
     * @param segment 版本段原文
     * @param text 拼接错误信息用的完整原文
     * @return 该段数值
     * @throws IllegalArgumentException 段为空、含非数字字符或数值溢出时抛出
     */
    private static int parseSegment(String segment, String text) {
        if (segment.isEmpty()) {
            throw new IllegalArgumentException("illegal api version: " + text);
        }
        for (int i = 0; i < segment.length(); i++) {
            char character = segment.charAt(i);
            if (character < '0' || character > '9') {
                throw new IllegalArgumentException("illegal api version: " + text);
            }
        }
        try {
            return Integer.parseInt(segment);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("illegal api version: " + text, exception);
        }
    }

    /**
     * 主版本号。
     *
     * @return 主版本数值
     */
    public int major() {
        return major;
    }

    /**
     * 次版本号，书写缺失时补 0。
     *
     * @return 次版本数值
     */
    public int minor() {
        return minor;
    }

    /**
     * 修订号，书写缺失时补 0。
     *
     * @return 修订数值
     */
    public int patch() {
        return patch;
    }

    /**
     * 与另一版本是否主版本相同（同一主版本线内视为次版本向后兼容候选）。
     *
     * @param other 待比较版本
     * @return 主版本相同返回 {@code true}；{@code other} 为 null 返回 {@code false}
     */
    public boolean isCompatibleWith(ApiVersion other) {
        if (Objects.isNull(other)) {
            return false;
        }
        return major == other.major;
    }

    /**
     * 数值逐段比较：先比主版本，再比次版本，最后比修订号。
     *
     * @param other 待比较版本（非 null）
     * @return 小于、等于、大于 {@code other} 时分别返回负数、0、正数
     */
    @Override
    public int compareTo(ApiVersion other) {
        Objects.requireNonNull(other, "other must not be null");
        int result = Integer.compare(major, other.major);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(minor, other.minor);
        if (result != 0) {
            return result;
        }
        return Integer.compare(patch, other.patch);
    }

    /**
     * 值相等判断：三段数值全等。
     *
     * @param o 待比较对象
     * @return 三段数值全等返回 {@code true}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ApiVersion)) {
            return false;
        }
        ApiVersion that = (ApiVersion) o;
        return major == that.major && minor == that.minor && patch == that.patch;
    }

    /**
     * 哈希码，与 {@link #equals(Object)} 保持一致的三段数值参与计算。
     *
     * @return 哈希码
     */
    @Override
    public int hashCode() {
        int result = Objects.hashCode(major);
        result = 31 * result + Objects.hashCode(minor);
        result = 31 * result + Objects.hashCode(patch);
        return result;
    }

    /**
     * 规范化文本表示，恒为三段：{@code 2.1} 输出为 {@code 2.1.0}。
     *
     * @return 形如 {@code major.minor.patch} 的字符串
     */
    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
