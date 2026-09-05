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

import java.util.Collection;

/**
 * 事件块读取器 SPI。
 *
 * <p>业务或框架适配层负责把具体事件存储（数据库、MQ、EventStoreDB、文件等）
 * 适配成该接口；核心层只关心从某个位置读取一批事件。
 *
 * @param <E> 事件类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public interface EventChunkReader<E> {
    EventChunk<E> read(String streamId, long fromEventNumber, int chunkSize, Collection<String> eventTypes);
}
