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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 单个依赖的就绪检查结果。
 *
 * <p>1.0.x（JDK8）实现说明：3.0.x 中本类为 record，JDK8 无 record 语法，
 * 降级为 final class + 手写 accessor/equals/hashCode/toString；
 * {@code Map.copyOf}/{@code Map.of}（Java 9+/10+）降级为
 * {@code Collections.unmodifiableMap(new LinkedHashMap<>(...))}，语义保持一致。
 *
 * @param name    稳定的依赖标识，例如 {@code postgresql} 或 {@code redis}
 * @param ready   是否可接受流量
 * @param details 可安全暴露的诊断信息，禁止放入密码、令牌或连接串
 */
public final class ReadinessResult {

    private final String name;
    private final boolean ready;
    private final Map<String, String> details;

    public ReadinessResult(String name, boolean ready, Map<String, String> details) {
        if (Objects.isNull(name) || name.trim().isEmpty()) {
            throw new IllegalArgumentException("readiness contributor name must not be blank");
        }
        this.name = name;
        this.ready = ready;
        this.details = Collections.unmodifiableMap(
                new LinkedHashMap<String, String>(Objects.isNull(details) ? Collections.<String, String>emptyMap() : details));
    }

    /**
     * 创建可接收流量的结果。
     *
     * @param name 依赖标识
     * @return 就绪结果
     */
    public static ReadinessResult ready(String name) {
        return new ReadinessResult(name, true, Collections.<String, String>emptyMap());
    }

    /**
     * 创建不可接收流量的结果。
     *
     * @param name   依赖标识
     * @param reason 可安全暴露的失败原因
     * @return 未就绪结果
     */
    public static ReadinessResult unavailable(String name, String reason) {
        return new ReadinessResult(name, false,
                (Objects.isNull(reason) || reason.trim().isEmpty())
                        ? Collections.<String, String>emptyMap()
                        : Collections.singletonMap("reason", reason));
    }

    public String name() {
        return name;
    }

    public boolean ready() {
        return ready;
    }

    public Map<String, String> details() {
        return details;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ReadinessResult)) {
            return false;
        }
        ReadinessResult that = (ReadinessResult) o;
        return ready == that.ready
                && Objects.equals(name, that.name)
                && Objects.equals(details, that.details);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, ready, details);
    }

    @Override
    public String toString() {
        return "ReadinessResult[name=" + name + ", ready=" + ready + ", details=" + details + "]";
    }
}
