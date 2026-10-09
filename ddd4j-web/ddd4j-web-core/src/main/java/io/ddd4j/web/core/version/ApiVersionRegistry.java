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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API 版本注册表：登记可用版本与其处理器（泛型句柄），供 {@link ApiVersionDispatchPolicy} 分发查询。
 *
 * <p>线程安全（{@link ConcurrentHashMap} 支撑），查询结果为不可变快照；处理器可以是任意业务句柄
 * （处理器对象、路由 key、灰度批次标识等），本注册表只做「版本 → 句柄」映射，不感知业务语义。
 *
 * @param <T> 处理器类型
 * @see ApiVersionDispatchPolicy
 */
public final class ApiVersionRegistry<T> {

    /** 版本到处理器的映射，线程安全。 */
    private final Map<ApiVersion, T> handlers = new ConcurrentHashMap<ApiVersion, T>();

    /**
     * 登记可用版本与处理器。
     *
     * @param version 可用版本（非 null）
     * @param handler 处理器句柄（非 null）
     * @return 当前注册表，支持链式登记
     * @throws IllegalArgumentException 同一版本重复登记时抛出
     */
    public ApiVersionRegistry<T> register(ApiVersion version, T handler) {
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
        T previous = handlers.putIfAbsent(version, handler);
        if (Objects.nonNull(previous)) {
            throw new IllegalArgumentException("api version already registered: " + version);
        }
        return this;
    }

    /**
     * 以版本文本登记，文本按 {@link ApiVersion#parse(String)} 解析。
     *
     * @param version 版本文本（如 {@code 2.1}、{@code v3}）
     * @param handler 处理器句柄（非 null）
     * @return 当前注册表，支持链式登记
     * @throws IllegalArgumentException 文本非法或同一版本重复登记时抛出
     */
    public ApiVersionRegistry<T> register(String version, T handler) {
        return register(ApiVersion.parse(version), handler);
    }

    /**
     * 查询指定主版本线下的全部可用版本（升序快照）。
     *
     * @param major 主版本号
     * @return 升序排列的可用版本列表，该主版本无注册时为空列表
     */
    public List<ApiVersion> versionsOfMajor(int major) {
        List<ApiVersion> matched = new ArrayList<ApiVersion>();
        for (ApiVersion version : handlers.keySet()) {
            if (version.major() == major) {
                matched.add(version);
            }
        }
        Collections.sort(matched);
        return Collections.unmodifiableList(matched);
    }

    /**
     * 升序列出全部已注册版本（快照）。
     *
     * @return 升序版本列表，未注册时为空列表
     */
    public List<ApiVersion> versions() {
        List<ApiVersion> all = new ArrayList<ApiVersion>(handlers.keySet());
        Collections.sort(all);
        return Collections.unmodifiableList(all);
    }

    /**
     * 精确查询版本对应的处理器。
     *
     * @param version 待查询版本（非 null）
     * @return 处理器句柄，未注册返回 null
     */
    public T find(ApiVersion version) {
        Objects.requireNonNull(version, "version must not be null");
        return handlers.get(version);
    }

    /**
     * 是否未登记任何版本。
     *
     * @return 未登记返回 {@code true}
     */
    public boolean isEmpty() {
        return handlers.isEmpty();
    }

    /**
     * 已登记的版本数量。
     *
     * @return 版本数量
     */
    public int size() {
        return handlers.size();
    }
}
