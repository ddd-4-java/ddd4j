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
package io.ddd4j.core.cqrs.readmodel;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import java.time.Instant;

/**
 * 投影最近一次运行的快照信息（由 {@link ProjectionMetrics} 实现方记录）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */

public final class ProjectionRunInfo {

    private static final long serialVersionUID = 0L;

    private final Instant lastRunAt;

    private final int lastEventCount;

    private final String lastError;

    /**
     * 返回最近运行时间，兼容 bean 调用方。
     */
    public Instant getLastRunAt() {
        return lastRunAt;
    }

    /**
     * 返回最近处理事件数。
     */
    public int getLastEventCount() {
        return lastEventCount;
    }

    /**
     * 返回最近错误信息。
     */
    public String getLastError() {
        return lastError;
    }

    /**
 * @param lastRunAt 上次运行完成时间
 * @param lastEventCount 上次运行处理的事件数量
 * @param lastError 上次运行失败的错误信息（成功时为 null）
 */

    @JsonCreator()
    public ProjectionRunInfo(@JsonProperty("lastRunAt") Instant lastRunAt, @JsonProperty("lastEventCount") int lastEventCount, @JsonProperty("lastError") String lastError) {
        this.lastRunAt = lastRunAt;
        this.lastEventCount = lastEventCount;
        this.lastError = lastError;
    }

    @JsonProperty("lastRunAt")
    public Instant lastRunAt() {
        return lastRunAt;
    }

    @JsonProperty("lastEventCount")
    public int lastEventCount() {
        return lastEventCount;
    }

    @JsonProperty("lastError")
    public String lastError() {
        return lastError;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        ProjectionRunInfo other = (ProjectionRunInfo) obj;
        return Objects.equals(this.lastRunAt, other.lastRunAt) && this.lastEventCount == other.lastEventCount && Objects.equals(this.lastError, other.lastError);
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(lastRunAt);
        result = 31 * result + Integer.hashCode(lastEventCount);
        result = 31 * result + Objects.hashCode(lastError);
        return result;
    }

    @Override
    public String toString() {
        return "ProjectionRunInfo[lastRunAt=" + lastRunAt + ", lastEventCount=" + lastEventCount + ", lastError=" + lastError + "]";
    }
}
