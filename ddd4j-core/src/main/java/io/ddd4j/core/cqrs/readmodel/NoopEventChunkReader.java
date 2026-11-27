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
 * 默认空事件读取器。
 *
 * <p>用于没有配置真实事件存储读取器时保持框架可启动。它不读取任何事件，也不会推进
 * 投影位置。
 *
 * @param <E> 事件类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class NoopEventChunkReader<E> implements EventChunkReader<E> {

    @Override
    public EventChunk<E> read(String streamId, long fromEventNumber, int chunkSize, Collection<String> eventTypes) {
        return EventChunk.empty(fromEventNumber);
    }
}
