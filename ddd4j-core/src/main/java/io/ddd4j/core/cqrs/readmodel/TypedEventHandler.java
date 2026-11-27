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

/**
 * 单一事件类型处理器。
 *
 * @param <E> 事件类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public interface TypedEventHandler<E> {

    /**
     * \u652f\u6301\u7684\u4e8b\u4ef6\u7c7b\u578b\u540d\u79f0\u3002
     *
     * @return \u4e8b\u4ef6\u7c7b\u578b\u540d\u79f0
     */
    String getEventType();

    /**
     * \u652f\u6301\u7684\u4e8b\u4ef6 Java \u7c7b\u578b\u3002
     *
     * @return \u4e8b\u4ef6 Java \u7c7b\u578b
     */
    Class<E> getEventClass();

    /**
     * 处理事件。
     *
     * @param event 事件
     */
    void handle(E event);
}
