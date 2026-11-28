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

    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    private static final long serialVersionUID = 0L;

    /** \u6295\u5f71\u4efb\u52a1\u4f7f\u7528\u7684\u4e8b\u4ef6\u6d41\u6807\u8bc6\u3002 */
    private final String streamId;

    /** \u6295\u5f71\u6062\u590d\u65f6\u5e94\u8bfb\u53d6\u7684\u4e0b\u4e00\u6761\u6e90\u4e8b\u4ef6\u5e8f\u53f7\u3002 */
    private final long nextEventNumber;

    /** \u5f53\u524d\u89c6\u56fe\u7684\u6295\u5f71\u8c03\u5ea6\u662f\u5426\u6b63\u5728\u8fd0\u884c\u3002 */
    private final boolean running;

    /** \u6700\u8fd1\u4e00\u6b21\u6267\u884c\u7684\u65f6\u95f4\u3002 */
    private final Instant lastRunAt;

    /** \u6700\u8fd1\u4e00\u6b21\u5904\u7406\u7684\u4e8b\u4ef6\u6570\u3002 */
    private final int lastEventCount;

    /** \u6700\u8fd1\u4e00\u6b21\u5931\u8d25\u7684\u8bca\u65ad\u4fe1\u606f\u3002 */
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

    /**
     * 根据<code>streamId</code>、<code>lastRunAt</code>、<code>lastError</code>、<code>nextEventNumber</code>、<code>running</code>、<code>lastEventCount</code>比较对象的值是否相等。
     *
     * <p>只比较运行时类型相同的对象。</p>
     *
     * @param obj 用来比较或转换的输入对象
     * @return 对象满足此实现的相等条件时为真
     */
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

    /**
     * 计算与对象相等条件一致的哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
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

    /**
     * 生成用于调试和日志的对象描述，包含<code>streamId</code>、<code>nextEventNumber</code>、<code>running</code>、<code>lastRunAt</code>、<code>lastEventCount</code>、<code>lastError</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "ProjectionStatus[streamId=" + streamId + ", nextEventNumber=" + nextEventNumber + ", running=" + running + ", lastRunAt=" + lastRunAt + ", lastEventCount=" + lastEventCount + ", lastError=" + lastError + "]";
    }
}
