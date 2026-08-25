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
package io.ddd4j.core.cqrs.eventstore;

import java.time.Instant;

/**
 * 已存储的事件记录。
 *
 * @param aggregateId 聚合根标识
 * @param version     事件在该聚合内的版本号（从 0 开始递增）
 * @param event       事件载荷（通常为领域事件对象）
 * @param position    全局递增位置（用于全局分页读取）
 * @param timestamp   事件存储时间
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public record StoredEvent(String aggregateId, long version, Object event, long position, Instant timestamp) {
}
