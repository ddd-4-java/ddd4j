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
package io.ddd4j.sample.order.application;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * 单次 Outbox 发布批次的结果。
 *
 * <p>{@code attempted} — 已尝试消息数
 * <p>{@code published} — 已确认消息数
 * <p>{@code failed} — 保留重试的失败消息数
 */
@Value
public class OutboxDispatchResult {
int attempted;
    int published;
    int failed;

    /** Creates a dispatch summary.
     * @param attempted attempted count
     * @param published published count
     * @param failed failed count
     */
    @JsonCreator
    public OutboxDispatchResult(@JsonProperty("attempted") int attempted,
                                @JsonProperty("published") int published,
                                @JsonProperty("failed") int failed) {
        this.attempted = attempted;
        this.published = published;
        this.failed = failed;
    }

    public int attempted() {
        return attempted;
    }

    public int published() {
        return published;
    }

    public int failed() {
        return failed;
    }
}
