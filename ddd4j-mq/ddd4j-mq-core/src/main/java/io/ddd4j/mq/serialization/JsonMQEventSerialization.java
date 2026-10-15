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
package io.ddd4j.mq.serialization;

import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.event.MQEventSerialization;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * 默认 JSON 消息序列化实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class JsonMQEventSerialization implements MQEventSerialization {

    /**
     * deserialize() 方法。
     * @param <S> 类型参数。
     * @param <T> 类型参数。
     * @param src src 参数。
     * @param dist 类型字面量。
     * @return 结果值。
     * @throws RuntimeException 出现异常时抛出。
     */
    @Override
    public <S, T> T deserialize(S src, Class<T> dist) throws RuntimeException {
        if (Objects.isNull(src)) {
            return null;
        }
        String text = src instanceof String s ? s : String.valueOf(src);
        if (StrKit.isEmpty(text)) {
            return null;
        }
        try {
            return JsonKit.DEFAULT_OBJECT_MAPPER.readValue(text, dist);
        } catch (Exception exception) {
            log.warn("Deserialize MQ event payload failed, return null: {}", exception.getMessage());
            return null;
        }
    }

    /**
     * serialize() 方法。
     * @param <T> 类型参数。
     * @param src 对象参数。
     * @return 结果值。
     * @throws RuntimeException 出现异常时抛出。
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T serialize(Object src) throws RuntimeException {
        if (Objects.isNull(src)) {
            return null;
        }
        try {
            return (T) JsonKit.DEFAULT_OBJECT_MAPPER.writeValueAsString(src);
        } catch (Exception exception) {
            throw new IllegalStateException("Serialize MQ event payload failed: " + exception.getMessage(), exception);
        }
    }
}
