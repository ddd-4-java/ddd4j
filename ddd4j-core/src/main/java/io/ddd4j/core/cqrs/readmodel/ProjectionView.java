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
     * \u89c6\u56fe\u540d\u79f0\u3002
     *
     * @return \u89c6\u56fe\u540d\u79f0
     */
    String getName();

    /**
     * \u6295\u5f71\u6d41 ID\u3002\u9ed8\u8ba4\u4f7f\u7528\u89c6\u56fe\u540d\u79f0\u3002
     *
     * @return \u6295\u5f71\u6d41\u6807\u8bc6
     */
    default String getStreamId() {
        return getName();
    }

    /**
     * \u5b9a\u65f6\u8c03\u5ea6 CRON \u8868\u8fbe\u5f0f\u3002
     *
     * @return \u5b9a\u65f6\u8c03\u5ea6\u8868\u8fbe\u5f0f
     */
    String getCron();

    /**
     * \u5355\u6b21\u8bfb\u53d6\u4e8b\u4ef6\u6570\u91cf\u3002
     *
     * @return \u5355\u6b21\u8bfb\u53d6\u7684\u4e8b\u4ef6\u6570\u91cf
     */
    default int getChunkSize() {
        return 100;
    }

    /**
     * \u672c\u89c6\u56fe\u5173\u6ce8\u7684\u4e8b\u4ef6\u7c7b\u578b\u3002
     *
     * @return \u89c6\u56fe\u5173\u6ce8\u7684\u4e8b\u4ef6\u7c7b\u578b\u96c6\u5408
     */
    Collection<String> getEventTypes();

    /**
     * 处理一批事件。
     *
     * @param events 事件列表
     */
    void handleEvents(Collection<E> events);
}
