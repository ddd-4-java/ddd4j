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
 * 框架无关的增量投影视图。
 *
 * @param <E> 事件类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public interface ProjectionView<E> {

    /**
     * 视图名称。
     */
    String getName();

    /**
     * 投影流 ID。默认使用视图名称。
     */
    default String getStreamId() {
        return getName();
    }

    /**
     * 定时调度 CRON 表达式。
     */
    String getCron();

    /**
     * 单次读取事件数量。
     */
    default int getChunkSize() {
        return 100;
    }

    /**
     * 本视图关注的事件类型。
     */
    Collection<String> getEventTypes();

    /**
     * 处理一批事件。
     *
     * @param events 事件列表
     */
    void handleEvents(Collection<E> events);
}
