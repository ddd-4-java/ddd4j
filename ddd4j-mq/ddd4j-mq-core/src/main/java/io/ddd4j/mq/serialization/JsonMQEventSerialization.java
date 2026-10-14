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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.event.MQEventSerialization;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * 默认 JSON 消息序列化实现。
 *
 * <p>领域事件基类携带 {@code ZonedDateTime}（{@code event-timestamp}），
 * 而 kit 的默认 mapper（无 jsr310 依赖）序列化它将失败并被吞为空串，
 * 导致 payload（含内嵌因果字段镜像）无法产出。本实现自持一个注册了
 * {@code JavaTimeModule} 的 mapper 副本，保证事件 payload 完整序列化。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class JsonMQEventSerialization implements MQEventSerialization {

    /**
     * 事件能力 mapper：基于 kit 默认 mapper 副本，注册 jsr310 并输出 ISO-8601 日期时间。
     *
     * <p>对应 OpenSpec change {@code promote-causality-to-broker-headers}：
     * payload 内嵌因果字段镜像（Requirement: Causality in broker headers 既有消费方兼容场景）
     * 依赖本 mapper 产出完整事件 JSON。
     */
    private static final ObjectMapper DOMAIN_EVENT_OBJECT_MAPPER = buildDomainEventObjectMapper();

    /**
     * 构建事件能力 mapper。
     *
     * @return 带 jsr310 支持的 mapper 副本
     */
    private static ObjectMapper buildDomainEventObjectMapper() {
        ObjectMapper mapper = JsonKit.DEFAULT_OBJECT_MAPPER.copy();
        mapper.registerModule(new JavaTimeModule());
        // 与 EventStore 侧一致输出 ISO-8601 文本，而非数值时间戳
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Override
    public <S, T> T deserialize(S src, Class<T> dist) throws RuntimeException {
        if (Objects.isNull(src)) {
            return null;
        }
        String text;
        if (src instanceof String) {
            text = (String) src;
        } else {
            text = String.valueOf(src);
        }
        if (StrKit.isEmpty(text)) {
            return null;
        }
        try {
            return DOMAIN_EVENT_OBJECT_MAPPER.readValue(text, dist);
        } catch (Exception e) {
            // 保持既有消费端契约：解析失败不抛出，返回 null 并记录告警
            log.warn("Deserialize MQ event payload failed, return null: {}", e.getMessage());
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T serialize(Object src) throws RuntimeException {
        return (T) toJsonString(src);
    }

    /**
     * 使用事件能力 mapper 序列化对象。
     *
     * @param src 源对象
     * @return JSON 文本；序列化失败抛出 {@link IllegalStateException}，不再吞为空串
     */
    private String toJsonString(Object src) {
        if (Objects.isNull(src)) {
            return null;
        }
        try {
            return DOMAIN_EVENT_OBJECT_MAPPER.writeValueAsString(src);
        } catch (Exception e) {
            throw new IllegalStateException("Serialize MQ event payload failed: " + e.getMessage(), e);
        }
    }
}
