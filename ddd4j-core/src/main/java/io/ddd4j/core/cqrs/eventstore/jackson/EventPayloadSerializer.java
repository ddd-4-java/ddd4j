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

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.ddd.event.DomainEvent;

import java.util.Objects;

/**
 * 领域事件 payload Jackson 序列化器（ADR-0005，硬化版本见
 * {@code docs/superpowers/specs/2026-08-25-eventstore-spi-hardening.md}）。
 *
 * <h3>类型安全的序列化策略</h3>
 * <p>本序列化器<b>不</b>使用 Jackson {@code activateDefaultTyping} / {@code @class} 多态
 * 标记——这是对早期版本（2.0.x 的 ddd4j-data-event-store 旧实现）的关键安全收紧。
 * 反序列化端始终由调用方显式传入目标 {@link DomainEvent} 子类型（{@link #deserialize(String, Class)}），
 * 既满足业务需要，又彻底关闭「{@code @class} 指向任意类」的多态反序列化攻击面（即便用
 * {@code BasicPolymorphicTypeValidator} 限定基类型，{@code DomainEvent} 子类若
 * 自身有危险的 {@code @JsonCreator} 仍可被利用）。
 *
 * <h3>类型丢失时的回退</h3>
 * <p>早期基于 {@code @class} 的多态反序列化策略下，序列化端会写入具体类名，
 * 反序列化端据此还原类型。新策略下序列化端不再写 {@code @class}，
 * 反序列化端完全依赖调用方传入的 {@code eventType}——这是更安全的契约。
 *
 * <h3>跨运行时共享</h3>
 * <p>ddd4j-core 在 2.0.x 主线继续使用 Jackson 2（{@code com.fasterxml.jackson}），
 * 本抽象不绑定任何运行时。3.0.x 主线已迁到 Jackson 3（{@code tools.jackson}），
 * 各自维护等价但 API 不同的实现。
 *
 * <h3>mapper 隔离</h3>
 * <p>构造器对传入 mapper 先执行 {@link ObjectMapper#copy()}——构造后传入的 source
 * mapper 保持不受影响（如 Spring 全局 mapper 不会被序列化器内部行为污染）。
 * {@code copy()} 会携带 source 已注册的全部 module（如 jsr310）与配置。
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
        this.objectMapper = Objects.requireNonNull(source, "source must not be null").copy();
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
    @SuppressWarnings("unchecked")
    public DomainEvent<?> deserialize(String json, Class<? extends DomainEvent<?>> eventType) {
        try {
            return objectMapper.readValue(json, eventType);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to deserialize event", e);
        }
    }
}