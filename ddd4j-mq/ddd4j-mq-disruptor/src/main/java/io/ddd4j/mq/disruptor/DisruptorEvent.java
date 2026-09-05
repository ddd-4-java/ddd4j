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
package io.ddd4j.mq.disruptor;

import io.ddd4j.kit.lang.StrKit;
import lombok.Getter;
import lombok.Setter;

/**
 * LMAX RingBuffer 中承载的 ddd4j 消息事件。
 *
 * <p>该事件模型由 ddd4j 自己维护，确保本地消息模块不依赖未发布的扩展库。</p>
 */
@Getter
@Setter
public class DisruptorEvent {

    private String namespace;
    private String topic;
    private String tag;
    private String messageId;
    private Object payload;
    private long sequence;

    public String getRouteExpression() {
        StringBuilder route = new StringBuilder();
        if (StrKit.isNotBlank(namespace)) {
            route.append(namespace).append('.');
        }
        route.append(topic);
        if (StrKit.isNotBlank(tag)) {
            route.append('.').append(tag);
        }
        return route.toString();
    }
}
