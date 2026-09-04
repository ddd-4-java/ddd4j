package io.ddd4j.mq.redisstream;

import java.util.Map;

/**
 * 跨 Jedis、Redisson 和 Lettuce 的统一 Redis Stream 记录模型。
 *
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
}
