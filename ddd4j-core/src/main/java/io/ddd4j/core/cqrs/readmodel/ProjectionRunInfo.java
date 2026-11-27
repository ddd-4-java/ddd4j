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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;

/**
 * 投影最近一次运行的快照信息（由 {@link ProjectionMetrics} 实现方记录）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */

public final class ProjectionRunInfo {

    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    private static final long serialVersionUID = 0L;

    /** \u6700\u8fd1\u4e00\u6b21\u8fd0\u884c\u5b8c\u6210\u6216\u5931\u8d25\u7684\u65f6\u95f4\u3002 */
    private final Instant lastRunAt;

    /** \u6700\u8fd1\u4e00\u6b21\u8bb0\u5f55\u5904\u7406\u7684\u4e8b\u4ef6\u6570\u3002 */
    private final int lastEventCount;

    /** \u6700\u8fd1\u4e00\u6b21\u8fd0\u884c\u5931\u8d25\u65f6\u4fdd\u5b58\u7684\u8bca\u65ad\u6d88\u606f\u3002 */
    private final String lastError;

    /**
     * @param lastRunAt      上次运行完成时间
     * @param lastEventCount 上次运行处理的事件数量
     * @param lastError      上次运行失败的错误信息（成功时为 null）
     */

    @JsonCreator()
    public ProjectionRunInfo(@JsonProperty("lastRunAt") Instant lastRunAt, @JsonProperty("lastEventCount") int lastEventCount, @JsonProperty("lastError") String lastError) {
        this.lastRunAt = lastRunAt;
        this.lastEventCount = lastEventCount;
        this.lastError = lastError;
    }

    /**
     * 返回最近运行时间，兼容 bean 调用方。
     *
     * @return 上次运行完成时间
     */
    public Instant getLastRunAt() {
        return lastRunAt;
    }

    /**
     * 返回最近处理事件数。
     *
     * @return 上次运行处理的事件数量
     */
    public int getLastEventCount() {
        return lastEventCount;
    }

    /**
     * 返回最近错误信息。
     *
     * @return 上次运行失败信息
     */
    public String getLastError() {
        return lastError;
    }

    /**
     * 获取用于 JSON 的上次运行完成时间。
     *
     * @return 上次运行完成时间
     */
    @JsonProperty("lastRunAt")
    public Instant lastRunAt() {
        return lastRunAt;
    }

    /**
     * 获取用于 JSON 的上次处理事件数。
     *
     * @return 上次处理事件数
     */
    @JsonProperty("lastEventCount")
    public int lastEventCount() {
        return lastEventCount;
    }

    /**
     * 获取用于 JSON 的上次运行失败信息。
     *
     * @return 上次失败信息
     */
    @JsonProperty("lastError")
    public String lastError() {
        return lastError;
    }

    /**
     * 根据<code>lastRunAt</code>、<code>lastError</code>、<code>lastEventCount</code>比较对象的值是否相等。
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
        ProjectionRunInfo other = (ProjectionRunInfo) obj;
        return Objects.equals(this.lastRunAt, other.lastRunAt) && this.lastEventCount == other.lastEventCount && Objects.equals(this.lastError, other.lastError);
    }

    /**
     * 计算与对象相等条件一致的哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(lastRunAt);
        result = 31 * result + Integer.hashCode(lastEventCount);
        result = 31 * result + Objects.hashCode(lastError);
        return result;
    }

    /**
     * 生成用于调试和日志的对象描述，包含<code>lastRunAt</code>、<code>lastEventCount</code>、<code>lastError</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "ProjectionRunInfo[lastRunAt=" + lastRunAt + ", lastEventCount=" + lastEventCount + ", lastError=" + lastError + "]";
    }
}
