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

package io.ddd4j.web.core.health;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Readiness HTTP 响应体，仅暴露整体状态，避免泄露下游依赖信息。
 *
 * <p>{@code ready} 表示当前应用是否可接收流量。
 */
public final class ReadinessResponse {

    private final boolean ready;

    /**
     * 创建 Readiness HTTP 响应体。
     *
     * @param ready 当前应用是否可接收流量
     */
    @JsonCreator
    public ReadinessResponse(@JsonProperty("ready") boolean ready) {
        this.ready = ready;
    }

    /**
     * 返回就绪状态。
     *
     * @return 就绪状态
     */
    @JsonProperty("ready")
    public boolean ready() {
        return ready;
    }

    /**
     * 返回探针应接收的 HTTP 状态码。
     *
     * @return 就绪时为 200，未就绪时为 503
     */
    public int httpStatus() {
        return ready ? 200 : 503;
    }

    /**
     * 返回就绪状态。
     *
     * @return 就绪状态
     */
    public boolean isReady() {
        return ready;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        ReadinessResponse other = (ReadinessResponse) obj;
        return ready == other.ready;
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(ready);
    }

    @Override
    public String toString() {
        return "ReadinessResponse[ready=" + ready + "]";
    }
}
