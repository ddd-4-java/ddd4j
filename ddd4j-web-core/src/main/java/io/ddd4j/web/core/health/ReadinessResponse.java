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

/**
 * Readiness HTTP 响应体，仅暴露整体状态，避免泄露下游依赖信息。
 *
 * @param ready 当前应用是否可接收流量
 */
public final class ReadinessResponse {
    private final boolean ready;
    public ReadinessResponse(boolean ready) { this.ready = ready; }
    public boolean ready() { return ready; }
    @Override public boolean equals(Object o) { return this==o || (o instanceof ReadinessResponse && ready==((ReadinessResponse)o).ready()); }
    @Override public int hashCode() { return java.util.Objects.hash(ready); }
    @Override public String toString() { return "ReadinessResponse{ready="+ready+"}"; }


    /**
     * 返回探针应接收的 HTTP 状态码。
     *
     * @return 就绪时为 200，未就绪时为 503
     */
    public int httpStatus() {
        return ready ? 200 : 503;
    }
}
