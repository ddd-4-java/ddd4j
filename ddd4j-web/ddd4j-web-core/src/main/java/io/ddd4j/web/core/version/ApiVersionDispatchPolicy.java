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

import java.util.List;
import java.util.Objects;

/**
 * API 版本分发策略：主版本分流 + 同主版本内次版本向后兼容，用于接口灰度分发。
 *
 * <p>分发规则（与规格 {@code api-version-dispatch} 对齐）：
 * <ul>
 *   <li><b>主版本分流</b>：请求主版本线在注册表中无任何可用版本时，返回 404 语义结果
 *       （绝不落到其他主版本）；</li>
 *   <li><b>次版本向后兼容</b>：同主版本线内取「不高于请求版本的最大可用版本」
 *       （注册 2.0/2.1/2.3、请求 2.2 时命中 2.1）；</li>
 *   <li><b>低于最低可用版本</b>：{@link ApiVersionMode#FALLBACK} 默认优雅回落到最低可用版本
 *       （结果标记 {@code degraded}/{@code upgradeHint}，状态 200），
 *       {@link ApiVersionMode#STRICT} 返回 400 拒绝结果。</li>
 * </ul>
 *
 * <p>无状态、线程安全，可安全复用单例。
 *
 * @see ApiVersionRegistry
 * @see ApiVersionDispatchResult
 */
public final class ApiVersionDispatchPolicy {

    /** 请求低于最低可用版本时的处理模式。 */
    private final ApiVersionMode mode;

    /**
     * 默认构造：回退（优雅回落）模式。
     */
    public ApiVersionDispatchPolicy() {
        this(ApiVersionMode.FALLBACK);
    }

    /**
     * 创建分发策略。
     *
     * @param mode 请求低于最低可用版本时的处理模式（非 null）
     */
    public ApiVersionDispatchPolicy(ApiVersionMode mode) {
        this.mode = Objects.requireNonNull(mode, "mode must not be null");
    }

    /**
     * 低于最低可用版本时的处理模式。
     *
     * @return 模式枚举
     */
    public ApiVersionMode mode() {
        return mode;
    }

    /**
     * 按冻结规则分发请求版本到注册表中的可用处理器。
     *
     * @param requested 解析出的请求版本（非 null）
     * @param registry 版本注册表（非 null）
     * @param <T> 处理器类型
     * @return 分发结果，携带命中版本、是否回落与 HTTP 语义状态码
     */
    public <T> ApiVersionDispatchResult<T> dispatch(ApiVersion requested, ApiVersionRegistry<T> registry) {
        Objects.requireNonNull(requested, "requested must not be null");
        Objects.requireNonNull(registry, "registry must not be null");
        List<ApiVersion> candidates = registry.versionsOfMajor(requested.major());
        if (candidates.isEmpty()) {
            return ApiVersionDispatchResult.notFound(requested);
        }
        //候选按升序排列，自尾部回扫即得「不高于请求版本的最大可用版本」。
        for (int i = candidates.size() - 1; i >= 0; i--) {
            ApiVersion candidate = candidates.get(i);
            if (candidate.compareTo(requested) <= 0) {
                return ApiVersionDispatchResult.matched(requested, candidate, registry.find(candidate));
            }
        }
        //同主版本线内请求版本低于全部可用版本。
        ApiVersion lowest = candidates.get(0);
        if (mode == ApiVersionMode.STRICT) {
            return ApiVersionDispatchResult.rejected(requested, lowest);
        }
        return ApiVersionDispatchResult.degraded(requested, lowest, registry.find(lowest));
    }
}
