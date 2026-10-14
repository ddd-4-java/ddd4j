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

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

/**
 * 单次 Outbox 发布批次的结果。
 */

public final class OutboxDispatchResult {

    private static final long serialVersionUID = 0L;

    private final int attempted;

    private final int published;

    private final int failed;

    /**
 * @param attempted 已尝试消息数
 * @param published 已确认消息数
 * @param failed 保留重试的失败消息数
 */

    @JsonCreator()
    public OutboxDispatchResult(@JsonProperty("attempted") int attempted, @JsonProperty("published") int published, @JsonProperty("failed") int failed) {
        this.attempted = attempted;
        this.published = published;
        this.failed = failed;
    }

    @JsonProperty("attempted")
    public int attempted() {
        return attempted;
    }

    @JsonProperty("published")
    public int published() {
        return published;
    }

    @JsonProperty("failed")
    public int failed() {
        return failed;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        OutboxDispatchResult other = (OutboxDispatchResult) obj;
        return this.attempted == other.attempted && this.published == other.published && this.failed == other.failed;
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Integer.hashCode(attempted);
        result = 31 * result + Integer.hashCode(published);
        result = 31 * result + Integer.hashCode(failed);
        return result;
    }

    @Override
    public String toString() {
        return "OutboxDispatchResult[attempted=" + attempted + ", published=" + published + ", failed=" + failed + "]";
    }
}
