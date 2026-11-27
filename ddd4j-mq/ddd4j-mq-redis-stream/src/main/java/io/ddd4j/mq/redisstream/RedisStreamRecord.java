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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Objects;

/**
 * 跨 Jedis、Redisson 和 Lettuce 的统一 Redis Stream 记录模型。
 *
 * <p>{@code stream} — 所属 Stream 名称
 * <p>{@code id} — 消息条目 ID
 * <p>{@code fields} — 消息字段
 * <p>{@code nativeMessage} — 底层原生消息对象
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class RedisStreamRecord {

    /** \u5f53\u524d\u6d88\u606f\u6240\u5c5e\u7684 Redis Stream \u952e\u3002 */
    private final String stream;
    /** \u5f53\u524d Redis Stream \u6761\u76ee\u7684\u552f\u4e00\u8bb0\u5f55\u6807\u8bc6\u3002 */
    private final String id;
    /** \u4ece Redis Stream \u6761\u76ee\u8bfb\u53d6\u7684\u5b57\u6bb5\u53ca\u6d88\u606f\u5143\u6570\u636e\u3002 */
    private final Map<String, String> fields;
    /** Redis \u5ba2\u6237\u7aef\u4f7f\u7528\u7684\u539f\u751f Stream \u6d88\u606f\u5bf9\u8c61\u3002 */
    private final Object nativeMessage;

    /**
     * 跨 Jedis、Redisson 和 Lettuce 的统一 Redis Stream 记录模型。
     *
     * @param stream        所属 Stream 名称
     * @param id            消息条目 ID
     * @param fields        消息字段
     * @param nativeMessage 底层原生消息对象
     */

    @JsonCreator
    public RedisStreamRecord(@JsonProperty("stream") String stream,
                             @JsonProperty("id") String id,
                             @JsonProperty("fields") Map<String, String> fields,
                             @JsonProperty("nativeMessage") Object nativeMessage) {
        this.stream = stream;
        this.id = id;
        this.fields = fields;
        this.nativeMessage = nativeMessage;
    }

    /**
     * 读取Redis Stream 名称。
     *
     * @return Redis Stream 名称
     */
    public String stream() {
        return stream;
    }

    /**
     * 读取Stream 记录 ID。
     *
     * @return Stream 记录 ID
     */
    public String id() {
        return id;
    }

    /**
     * 读取消息字段。
     *
     * @return 消息字段
     */
    public Map<String, String> fields() {
        return fields;
    }

    /**
     * 读取底层原生消息对象。
     *
     * @return 底层原生消息对象
     */
    public Object nativeMessage() {
        return nativeMessage;
    }

    /**
     * 根据<code>stream</code>、<code>id</code>、<code>fields</code>、<code>nativeMessage</code>比较对象的值是否相等。
     *
     * @param o 用来比较的另一个对象，允许为空
     * @return 对象满足此实现的相等条件时为真
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RedisStreamRecord)) {
            return false;
        }
        RedisStreamRecord that = (RedisStreamRecord) o;
        return Objects.equals(stream, that.stream)
                && Objects.equals(id, that.id)
                && Objects.equals(fields, that.fields)
                && Objects.equals(nativeMessage, that.nativeMessage);
    }

    /**
     * 计算与对象相等条件一致的哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
    @Override
    public int hashCode() {
        int result = Objects.hashCode(stream);
        result = 31 * result + Objects.hashCode(id);
        result = 31 * result + Objects.hashCode(fields);
        return 31 * result + Objects.hashCode(nativeMessage);
    }

    /**
     * 生成用于调试和日志的对象描述，包含<code>stream</code>、<code>id</code>、<code>fields</code>、<code>nativeMessage</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "RedisStreamRecord[stream=" + stream + ", id=" + id + ", fields=" + fields
                + ", nativeMessage=" + nativeMessage + ']';
    }

    /**
     * 读取Redis Stream 名称。
     *
     * @return Redis Stream 名称
     */
    public String getStream() {
        return stream;
    }

    /**
     * 读取Stream 记录 ID。
     *
     * @return Stream 记录 ID
     */
    public String getId() {
        return id;
    }

    /**
     * 读取消息字段。
     *
     * @return 消息字段
     */
    public Map<String, String> getFields() {
        return fields;
    }

    /**
     * 读取底层原生消息对象。
     *
     * @return 底层原生消息对象
     */
    public Object getNativeMessage() {
        return nativeMessage;
    }
}
