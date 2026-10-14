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
package io.ddd4j.core.health;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.ddd4j.kit.lang.StrKit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 单个依赖的就绪检查结果。
 */

public final class ReadinessResult {

    private static final long serialVersionUID = 0L;

    private final String name;

    private final boolean ready;

    private final Map<String, String> details;

    /**
 * @param name 稳定的依赖标识，例如 {@code postgresql} 或 {@code redis}
 * @param ready 是否可接受流量
 * @param details 可安全暴露的诊断信息，禁止放入密码、令牌或连接串
 */

    @JsonCreator()
    public ReadinessResult(@JsonProperty("name") String name, @JsonProperty("ready") boolean ready, @JsonProperty("details") Map<String, String> details) {
        if (StrKit.isBlank(name)) {
            throw new IllegalArgumentException("readiness contributor name must not be blank");
        }
        this.name = name;
        this.ready = ready;
        Map<String, String> copiedDetails = new LinkedHashMap<>();
        if (Objects.nonNull(details)) {
            for (Map.Entry<String, String> entry : details.entrySet()) {
                copiedDetails.put(Objects.requireNonNull(entry.getKey(), "detail key"),
                        Objects.requireNonNull(entry.getValue(), "detail value"));
            }
        }
        this.details = Collections.unmodifiableMap(copiedDetails);
    }

    /**
     * 创建可接收流量的结果。
     *
     * @param name 依赖标识
     * @return 就绪结果
     */
    public static ReadinessResult ready(String name) {
        return new ReadinessResult(name, true, Map.of());
    }

    /**
     * 创建不可接收流量的结果。
     *
     * @param name   依赖标识
     * @param reason 可安全暴露的失败原因
     * @return 未就绪结果
     */
    public static ReadinessResult unavailable(String name, String reason) {
        return new ReadinessResult(name, false, StrKit.isBlank(reason) ? Map.of() : Map.of("reason", reason));
    }

    /**
     * 获取诊断详情。
     *
     * @return 不可变诊断详情
     */
    public Map<String, String> getDetails() {
        return details;
    }

    /**
     * 获取依赖名称。
     *
     * @return 依赖名称
     */
    public String getName() {
        return name;
    }

    /**
     * 判断该依赖是否可接收流量。
     *
     * @return 就绪时为 true
     */
    public boolean isReady() {
        return ready;
    }

    /**
     * 获取用于 JSON 的依赖名称。
     *
     * @return 依赖名称
     */
    @JsonProperty("name")
    public String name() {
        return name;
    }

    /**
     * 获取用于 JSON 的就绪状态。
     *
     * @return 就绪时为 true
     */
    @JsonProperty("ready")
    public boolean ready() {
        return ready;
    }

    /**
     * 获取用于 JSON 的诊断详情。
     *
     * @return 不可变诊断详情
     */
    @JsonProperty("details")
    public Map<String, String> details() {
        return details;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        ReadinessResult other = (ReadinessResult) obj;
        return Objects.equals(this.name, other.name) && this.ready == other.ready && Objects.equals(this.details, other.details);
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(name);
        result = 31 * result + Boolean.hashCode(ready);
        result = 31 * result + Objects.hashCode(details);
        return result;
    }

    @Override
    public String toString() {
        return "ReadinessResult[name=" + name + ", ready=" + ready + ", details=" + details + "]";
    }
}
