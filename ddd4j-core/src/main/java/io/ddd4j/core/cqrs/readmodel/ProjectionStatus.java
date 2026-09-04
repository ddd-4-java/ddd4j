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
 * @param streamId        投影流 ID
 * @param nextEventNumber 下一个待处理事件号（0-based）
 * @param running         视图管理器是否处于运行状态
 * @param lastRunAt       上次运行完成时间（nullable，首次未运行时为 null）
 * @param lastEventCount  上次运行处理的事件数量（0 表示空或未运行）
 * @param lastError       上次运行失败的错误信息（nullable，成功时为 null）
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
public record ProjectionStatus(
        String streamId,
        long nextEventNumber,
        boolean running,
        Instant lastRunAt,
        int lastEventCount,
        String lastError
) {

    /**
     * 紧凑构造器，校验参数合法性。
     */
    public ProjectionStatus {
        Objects.requireNonNull(streamId, "streamId must not be null");
        if (nextEventNumber < 0) {
            throw new IllegalArgumentException("nextEventNumber must not be negative");
        }
        if (lastEventCount < 0) {
            throw new IllegalArgumentException("lastEventCount must not be negative");
        }
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
}
