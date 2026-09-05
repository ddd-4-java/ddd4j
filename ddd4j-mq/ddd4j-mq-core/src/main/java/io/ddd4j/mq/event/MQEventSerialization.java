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
package io.ddd4j.mq.event;

/**
 * MQ 事件序列化/反序列化契约。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface MQEventSerialization {

    /**
     * 反序列化消息体为领域对象。
     *
     * @param src  原始消息体
     * @param dist 目标类型
     * @param <S>  源类型
     * @param <T>  目标类型
     * @return 反序列化结果
     */
    <S, T> T deserialize(S src, Class<T> dist) throws RuntimeException;

    /**
     * 序列化领域对象为消息体。
     *
     * @param src 源对象
     * @param <T> 序列化结果类型
     * @return 序列化结果
     */
    <T> T serialize(Object src) throws RuntimeException;
}
