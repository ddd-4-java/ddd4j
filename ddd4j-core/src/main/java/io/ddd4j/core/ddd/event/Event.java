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
package io.ddd4j.core.ddd.event;

import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * 事件共有元数据契约。
 */
public interface Event extends Serializable {

    /**
     * @return 全局事件标识
     */
    EventId getEventId();

    /**
     * @return 事件类型
     */
    EventType getEventType();

    /**
     * @return 事件产生时间
     */
    ZonedDateTime getEventTimestamp();

    /**
     * @return 关联事件标识；没有时返回 {@code null}
     */
    EventId getCorrelationId();

    /**
     * @return 直接因果事件标识；没有时返回 {@code null}
     */
    EventId getCausationId();

}
