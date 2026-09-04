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

/**
 * 投影最近一次运行的快照信息（由 {@link ProjectionMetrics} 实现方记录）。
 *
 * @param lastRunAt      上次运行完成时间
 * @param lastEventCount 上次运行处理的事件数量
 * @param lastError      上次运行失败的错误信息（成功时为 null）
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
public record ProjectionRunInfo(
        Instant lastRunAt,
        int lastEventCount,
        String lastError
) {
}
