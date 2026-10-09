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
 * API 版本解析结果：携带解析出的版本、来源与原始文本。
 *
 * <p>不可变、线程安全，由 {@link ApiVersionResolver} 产出。
 *
 * @see ApiVersionResolver
 */
public final class ApiVersionResult {

    /** 解析出的版本，恒非 null。 */
    private final ApiVersion version;

    /** 版本来源。 */
    private final ApiVersionSource source;

    /** 原始文本（请求头原文 / 路径版本段 / 默认版本文本），可为 null。 */
    private final String rawValue;

    /**
     * 创建解析结果。
     *
     * @param version 解析出的版本（非 null）
     * @param source 版本来源（非 null）
     * @param rawValue 触发本次结果的原始文本，可为 null
     */
    public ApiVersionResult(ApiVersion version, ApiVersionSource source, String rawValue) {
        this.version = Objects.requireNonNull(version, "version must not be null");
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.rawValue = rawValue;
    }

    /**
     * 解析出的版本。
     *
     * @return 版本值对象，恒非 null
     */
    public ApiVersion version() {
        return version;
    }

    /**
     * 版本来源。
     *
     * @return 来源枚举
     */
    public ApiVersionSource source() {
        return source;
    }

    /**
     * 原始文本。
     *
     * @return 请求头原文、路径版本段或默认版本文本，可为 null
     */
    public String rawValue() {
        return rawValue;
    }

    /**
     * 值相等判断：版本、来源、原始文本全等。
     *
     * @param o 待比较对象
     * @return 全等返回 {@code true}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ApiVersionResult)) {
            return false;
        }
        ApiVersionResult that = (ApiVersionResult) o;
        return Objects.equals(version, that.version)
                && source == that.source
                && Objects.equals(rawValue, that.rawValue);
    }

    /**
     * 哈希码，与 {@link #equals(Object)} 保持一致。
     *
     * @return 哈希码
     */
    @Override
    public int hashCode() {
        return Objects.hash(version, source, rawValue);
    }

    /**
     * 文本表示，便于日志输出与断言。
     *
     * @return 形如 {@code ApiVersionResult[version=2.1.0, source=HEADER, rawValue=2.1.0]} 的字符串
     */
    @Override
    public String toString() {
        return "ApiVersionResult[version=" + version + ", source=" + source + ", rawValue=" + rawValue + "]";
    }
}
