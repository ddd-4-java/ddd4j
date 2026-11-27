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

    /** \u4f5c\u4e3a\u7269\u7406\u4e3b\u9898\u540d\u524d\u7f00\u4f7f\u7528\u7684\u547d\u540d\u7a7a\u95f4\u3002 */
    private String namespace;
    /** \u6d88\u606f\u6295\u9012\u6216\u8ba2\u9605\u7684\u4e3b\u9898\u3002 */
    private String topic;
    /** \u6d88\u606f\u7684\u8def\u7531\u6807\u7b7e\u3002 */
    private String tag;
    /** \u6d88\u606f\u6807\u8bc6\u3002 */
    private String messageId;
    /** \u6d88\u606f\u8f6c\u79fb\u6216\u53d1\u9001\u65f6\u4f7f\u7528\u7684\u5e8f\u5217\u5316\u8d1f\u8f7d\u3002 */
    private Object payload;
    /** \u5f53\u524d\u4e8b\u4ef6\u5728 Disruptor \u73af\u5f62\u7f13\u51b2\u533a\u4e2d\u7684\u5e8f\u53f7\u3002 */
    private long sequence;

    /**
     * \u62fc\u63a5\u6d88\u606f\u7684\u8def\u7531\u8868\u8fbe\u5f0f\u3002
     * @return \u7531\u547d\u540d\u7a7a\u95f4\u3001\u4e3b\u9898\u548c\u6807\u7b7e\u7ec4\u6210\u7684\u8def\u7531\u8868\u8fbe\u5f0f
     */
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
