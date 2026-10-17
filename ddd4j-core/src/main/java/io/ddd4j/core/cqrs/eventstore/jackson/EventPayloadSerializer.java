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
package io.ddd4j.core.cqrs.eventstore.jackson;

import io.ddd4j.core.ddd.event.DomainEvent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * 领域事件 payload Jackson 序列化器（ADR-0005，硬化版本见
 * {@code docs/superpowers/specs/2026-08-25-eventstore-spi-hardening.md}）。
 *
 * <p><b>类型安全的序列化策略</b></p>
 * <p>序列化结果不包含 {@code @class} 多态类型标记；反序列化必须由调用方显式传入
 * {@link DomainEvent} 的目标子类型。JSON 中的任意类名不能选择反序列化目标。
 *
 * <p><b>向前兼容与映射器隔离</b></p>
 * <p>内部映射器允许读取未来版本新增的未知字段。构造器基于调用方传入的映射器创建
 * 独立实例，继承其模块与配置；内部设置不修改传入的映射器。三线分别使用本线
 * Jackson 2 或 Jackson 3 提供的映射器复制接口。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class EventPayloadSerializer {

    private final ObjectMapper objectMapper;

    /**
     * 创建序列化器。
     *
     * <p>内部使用传入 mapper 的副本构造，避免污染调用方的全局 mapper。
     *
     * @param source 源 mapper；构造后保持不变
     */
    public EventPayloadSerializer(ObjectMapper source) {
        this.objectMapper = Objects.requireNonNull(source, "source must not be null")
                .rebuild()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    /**
     * 序列化领域事件。
     *
     * <p>输出 JSON 中<b>不</b>包含 {@code @class} 多态标记——
     * 反序列化端必须通过 {@link #deserialize(String, Class)} 显式传入目标类型。
     *
     * @param event 领域事件
     * @return 不含多态标记的 JSON 文本
     * @throws IllegalStateException 序列化失败
     */
    public String serialize(DomainEvent<?> event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize event", e);
        }
    }

    /**
     * 反序列化领域事件。
     *
     * <p>使用调用方提供的 {@code eventType} 还原目标类型，无需依赖 JSON 中的类型标记。
     * 调用方负责保证 {@code eventType} 与实际写入的事件类型一致（通常通过
     * 事件实际运行时类型或持久化时记录的类名获取）。
     *
     * @param json      JSON 文本（不含多态标记）
     * @param eventType 目标事件类型
     * @return 还原的领域事件
     * @throws IllegalStateException 反序列化失败
     */
    public DomainEvent<?> deserialize(String json, Class<? extends DomainEvent<?>> eventType) {
        try {
            return objectMapper.readValue(json, eventType);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to deserialize event", e);
        }
    }
}
