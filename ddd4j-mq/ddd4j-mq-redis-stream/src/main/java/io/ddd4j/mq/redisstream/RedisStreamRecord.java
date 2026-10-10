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
package io.ddd4j.mq.redisstream;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import java.util.Map;

/**
 * 跨 Jedis、Redisson 和 Lettuce 的统一 Redis Stream 记录模型。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */

public final class RedisStreamRecord {

    private static final long serialVersionUID = 0L;

    private final String stream;

    private final String id;

    private final Map<String, String> fields;

    private final Object nativeMessage;

    /**
     * 返回流名称，兼容 bean 调用方。
     */
    public String getStream() {
        return stream;
    }

    /**
     * 返回条目标识。
     */
    public String getId() {
        return id;
    }

    /**
     * 返回消息字段。
     */
    public Map<String, String> getFields() {
        return fields;
    }

    /**
     * 返回底层消息对象。
     */
    public Object getNativeMessage() {
        return nativeMessage;
    }

    /**
 * @param stream 所属 Stream 名称
 * @param id 消息条目 ID
 * @param fields 消息字段
 * @param nativeMessage 底层原生消息对象
 */

    @JsonCreator()
    public RedisStreamRecord(@JsonProperty("stream") String stream, @JsonProperty("id") String id, @JsonProperty("fields") Map<String, String> fields, @JsonProperty("nativeMessage") Object nativeMessage) {
        this.stream = stream;
        this.id = id;
        this.fields = fields;
        this.nativeMessage = nativeMessage;
    }

    @JsonProperty("stream")
    public String stream() {
        return stream;
    }

    @JsonProperty("id")
    public String id() {
        return id;
    }

    @JsonProperty("fields")
    public Map<String, String> fields() {
        return fields;
    }

    @JsonProperty("nativeMessage")
    public Object nativeMessage() {
        return nativeMessage;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        RedisStreamRecord other = (RedisStreamRecord) obj;
        return Objects.equals(this.stream, other.stream) && Objects.equals(this.id, other.id) && Objects.equals(this.fields, other.fields) && Objects.equals(this.nativeMessage, other.nativeMessage);
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(stream);
        result = 31 * result + Objects.hashCode(id);
        result = 31 * result + Objects.hashCode(fields);
        result = 31 * result + Objects.hashCode(nativeMessage);
        return result;
    }

    @Override
    public String toString() {
        return "RedisStreamRecord[stream=" + stream + ", id=" + id + ", fields=" + fields + ", nativeMessage=" + nativeMessage + "]";
    }
}
