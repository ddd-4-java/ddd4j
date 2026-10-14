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

import java.time.Instant;
import java.util.Objects;

/**
 * 投影视图实时状态快照（不可变值对象）。
 *
 * <p>由 {@link ViewManager#getProjectionStatus(String)} 返回，供运维 / 监控层
 * 查询单个投影视图的当前进度与健康状况。
 *
 * <p>各运行时实现可覆写 {@code getProjectionStatus()} 返回真实状态；
 * 未覆写时 {@link ViewManager} 的 default 实现返回基线状态（nextEventNumber=0）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */

public final class ProjectionStatus {

    private static final long serialVersionUID = 0L;

    private final String streamId;

    private final long nextEventNumber;

    private final boolean running;

    private final Instant lastRunAt;

    private final int lastEventCount;

    private final String lastError;

    /**
 * 紧凑构造器，校验参数合法性。
 *
 * @param streamId 投影流 ID
 * @param nextEventNumber 下一个待处理事件号（0-based）
 * @param running 视图管理器是否处于运行状态
 * @param lastRunAt 上次运行完成时间（nullable，首次未运行时为 null）
 * @param lastEventCount 上次运行处理的事件数量（0 表示空或未运行）
 * @param lastError 上次运行失败的错误信息（nullable，成功时为 null）
 */

    @JsonCreator()
    public ProjectionStatus(@JsonProperty("streamId") String streamId, @JsonProperty("nextEventNumber") long nextEventNumber, @JsonProperty("running") boolean running, @JsonProperty("lastRunAt") Instant lastRunAt, @JsonProperty("lastEventCount") int lastEventCount, @JsonProperty("lastError") String lastError) {
        Objects.requireNonNull(streamId, "streamId must not be null");
        if (nextEventNumber < 0) {
            throw new IllegalArgumentException("nextEventNumber must not be negative");
        }
        if (lastEventCount < 0) {
            throw new IllegalArgumentException("lastEventCount must not be negative");
        }
        this.streamId = streamId;
        this.nextEventNumber = nextEventNumber;
        this.running = running;
        this.lastRunAt = lastRunAt;
        this.lastEventCount = lastEventCount;
        this.lastError = lastError;
    }

    /**
     * 创建基线状态（nextEventNumber=0，无运行历史）。
     *
     * <p>适用于未跟踪的视图或 {@link ViewManager} default 实现。
     *
     * @param streamId 投影流 ID
     * @param running  是否处于运行状态
     * @return 基线状态
     */
    public static ProjectionStatus baseline(String streamId, boolean running) {
        return new ProjectionStatus(streamId, 0, running, null, 0, null);
    }

    /**
     * 获取上次运行失败信息。
     *
     * @return 上次运行失败信息
     */
    public String getLastError() {
        return lastError;
    }

    /**
     * 获取上次运行处理的事件数。
     *
     * @return 上次运行处理的事件数
     */
    public int getLastEventCount() {
        return lastEventCount;
    }

    /**
     * 获取上次运行完成时间。
     *
     * @return 上次运行完成时间
     */
    public Instant getLastRunAt() {
        return lastRunAt;
    }

    /**
     * 获取下一个待处理事件号。
     *
     * @return 下一个待处理事件号
     */
    public long getNextEventNumber() {
        return nextEventNumber;
    }

    /**
     * 获取投影流标识。
     *
     * @return 投影流标识
     */
    public String getStreamId() {
        return streamId;
    }

    /**
     * 获取视图管理器运行状态。
     *
     * @return 视图管理器运行状态
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * 获取用于 JSON 的投影流标识。
     *
     * @return 用于 JSON 的投影流标识
     */
    @JsonProperty("streamId")
    public String streamId() {
        return streamId;
    }

    /**
     * 获取用于 JSON 的下一个待处理事件号。
     *
     * @return 用于 JSON 的下一个待处理事件号
     */
    @JsonProperty("nextEventNumber")
    public long nextEventNumber() {
        return nextEventNumber;
    }

    /**
     * 获取用于 JSON 的运行状态。
     *
     * @return 用于 JSON 的运行状态
     */
    @JsonProperty("running")
    public boolean running() {
        return running;
    }

    /**
     * 获取用于 JSON 的上次运行完成时间。
     *
     * @return 用于 JSON 的上次运行完成时间
     */
    @JsonProperty("lastRunAt")
    public Instant lastRunAt() {
        return lastRunAt;
    }

    /**
     * 获取用于 JSON 的上次运行处理事件数。
     *
     * @return 用于 JSON 的上次运行处理事件数
     */
    @JsonProperty("lastEventCount")
    public int lastEventCount() {
        return lastEventCount;
    }

    /**
     * 获取用于 JSON 的上次运行失败信息。
     *
     * @return 用于 JSON 的上次运行失败信息
     */
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
        ProjectionStatus other = (ProjectionStatus) obj;
        return Objects.equals(this.streamId, other.streamId) && this.nextEventNumber == other.nextEventNumber && this.running == other.running && Objects.equals(this.lastRunAt, other.lastRunAt) && this.lastEventCount == other.lastEventCount && Objects.equals(this.lastError, other.lastError);
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(streamId);
        result = 31 * result + Long.hashCode(nextEventNumber);
        result = 31 * result + Boolean.hashCode(running);
        result = 31 * result + Objects.hashCode(lastRunAt);
        result = 31 * result + Integer.hashCode(lastEventCount);
        result = 31 * result + Objects.hashCode(lastError);
        return result;
    }

    @Override
    public String toString() {
        return "ProjectionStatus[streamId=" + streamId + ", nextEventNumber=" + nextEventNumber + ", running=" + running + ", lastRunAt=" + lastRunAt + ", lastEventCount=" + lastEventCount + ", lastError=" + lastError + "]";
    }
}
