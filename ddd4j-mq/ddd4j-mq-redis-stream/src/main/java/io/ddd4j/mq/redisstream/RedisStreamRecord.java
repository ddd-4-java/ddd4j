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

import java.util.Map;

/**
 * 跨 Jedis、Redisson 和 Lettuce 的统一 Redis Stream 记录模型。
 *
 * @param stream        所属 Stream 名称
 * @param id            消息条目 ID
 * @param fields        消息字段
 * @param nativeMessage 底层原生消息对象
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class RedisStreamRecord {

    private final String stream;
    private final String id;
    private final Map<String, String> fields;
    private final Object nativeMessage;

    public RedisStreamRecord(String stream, String id, Map<String, String> fields, Object nativeMessage) {
        this.stream = stream;
        this.id = id;
        this.fields = fields;
        this.nativeMessage = nativeMessage;
    }

    public String stream() { return stream; }
    public String id() { return id; }
    public Map<String, String> fields() { return fields; }
    public Object nativeMessage() { return nativeMessage; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RedisStreamRecord)) return false;
        RedisStreamRecord that = (RedisStreamRecord) o;
        return stream.equals(that.stream) && id.equals(that.id) && fields.equals(that.fields);
    }

    @Override
    public int hashCode() {
        int result = stream.hashCode();
        result = 31 * result + id.hashCode();
        result = 31 * result + fields.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "RedisStreamRecord{stream=" + stream + ", id=" + id + ", fields=" + fields + '}';
    }

    public String getStream() {
        return stream;
    }

    public String getId() {
        return id;
    }

    public Map<String, String> getFields() {
        return fields;
    }

    public Object getNativeMessage() {
        return nativeMessage;
    }
}
