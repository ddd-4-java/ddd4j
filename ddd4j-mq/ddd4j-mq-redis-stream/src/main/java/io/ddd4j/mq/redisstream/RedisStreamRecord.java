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

    /** Java 序列化版本标识。 */
    private static final long serialVersionUID = 0L;

    /** \u5f53\u524d\u6d88\u606f\u6240\u5c5e\u7684 Redis Stream \u952e\u3002 */
    private final String stream;

    /** \u5f53\u524d Redis Stream \u6761\u76ee\u7684\u552f\u4e00\u8bb0\u5f55\u6807\u8bc6\u3002 */
    private final String id;

    /** \u4ece Redis Stream \u6761\u76ee\u8bfb\u53d6\u7684\u5b57\u6bb5\u53ca\u6d88\u606f\u5143\u6570\u636e\u3002 */
    private final Map<String, String> fields;

    /** Redis \u5ba2\u6237\u7aef\u4f7f\u7528\u7684\u539f\u751f Stream \u6d88\u606f\u5bf9\u8c61\u3002 */
    private final Object nativeMessage;

    /**
     * 初始化 <code>RedisStreamRecord</code> 使用的 <code>stream</code>、<code>id</code>、<code>fields</code>、<code>nativeMessage</code>。
     *
     * @param stream \u6240\u5c5e Stream \u540d\u79f0
     * @param id \u6d88\u606f\u6761\u76ee ID
     * @param fields \u6d88\u606f\u5b57\u6bb5
     * @param nativeMessage \u5e95\u5c42\u539f\u751f\u6d88\u606f\u5bf9\u8c61
     */

    @JsonCreator()
    public RedisStreamRecord(@JsonProperty("stream") String stream, @JsonProperty("id") String id, @JsonProperty("fields") Map<String, String> fields, @JsonProperty("nativeMessage") Object nativeMessage) {
        this.stream = stream;
        this.id = id;
        this.fields = fields;
        this.nativeMessage = nativeMessage;
    }

    /**
     * \u8bfb\u53d6Redis Stream \u540d\u79f0\u3002
     * @return Redis Stream \u540d\u79f0
     */
    @JsonProperty("stream")
    public String stream() {
        return stream;
    }

    /**
     * \u8bfb\u53d6Stream \u8bb0\u5f55 ID\u3002
     * @return Stream \u8bb0\u5f55 ID
     */
    @JsonProperty("id")
    public String id() {
        return id;
    }

    /**
     * \u8bfb\u53d6\u6d88\u606f\u5b57\u6bb5\u3002
     * @return \u6d88\u606f\u5b57\u6bb5
     */
    @JsonProperty("fields")
    public Map<String, String> fields() {
        return fields;
    }

    /**
     * \u8bfb\u53d6\u5e95\u5c42\u539f\u751f\u6d88\u606f\u5bf9\u8c61\u3002
     * @return \u5e95\u5c42\u539f\u751f\u6d88\u606f\u5bf9\u8c61
     */
    @JsonProperty("nativeMessage")
    public Object nativeMessage() {
        return nativeMessage;
    }

    /**
     * 执行 <code>equals</code>，调用 <code>obj.getClass</code>。
     *
     * @param obj 传入此操作的 <code>Object</code> 类型 <code>obj</code>
     * @return 按条件返回 <code>true</code>、<code>false</code>
     */
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

    /**
     * 计算与对象相等条件一致的哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(stream);
        result = 31 * result + Objects.hashCode(id);
        result = 31 * result + Objects.hashCode(fields);
        result = 31 * result + Objects.hashCode(nativeMessage);
        return result;
    }

    /**
     * 生成用于调试和日志的对象描述，包含<code>stream</code>、<code>id</code>、<code>fields</code>、<code>nativeMessage</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "RedisStreamRecord[stream=" + stream + ", id=" + id + ", fields=" + fields + ", nativeMessage=" + nativeMessage + "]";
    }
}
