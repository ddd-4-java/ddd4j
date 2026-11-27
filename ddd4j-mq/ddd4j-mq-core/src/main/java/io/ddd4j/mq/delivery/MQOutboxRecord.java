package io.ddd4j.mq.delivery;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.ddd4j.kit.lang.StrKit;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 可持久化的 Outbox 消息快照。
 *
 * <p>该对象不绑定 JSON、数据库或 broker；存储适配器负责将其映射为自己的表结构。
 */
public final class MQOutboxRecord {

    /** Java 序列化版本标识。 */
    private static final long serialVersionUID = 0L;

    /** \u6d88\u606f\u6807\u8bc6\u3002 */
    private final String messageId;

    /** \u8bb0\u5f55\u53d1\u5e03\u65f6\u4f7f\u7528\u7684\u76ee\u6807\u4e3b\u9898\u6216\u5730\u5740\u3002 */
    private final String destination;

    /** \u4e8b\u4ef6\u5199\u5165 Outbox \u65f6\u5e8f\u5217\u5316\u7684\u6d88\u606f\u8d1f\u8f7d\u3002 */
    private final String payload;

    /** \u6295\u9012\u8bb0\u5f55\u643a\u5e26\u7684\u6d88\u606f\u5143\u6570\u636e\u3002 */
    private final Map<String, String> headers;

    /** \u5f53\u524d Outbox \u8bb0\u5f55\u7684\u6295\u9012\u72b6\u6001\u3002 */
    private final MQOutboxStatus status;

    /** \u8bb0\u5f55\u4e0b\u6b21\u53ef\u88ab\u8ba4\u9886\u6295\u9012\u7684\u65f6\u95f4\u3002 */
    private final Instant availableAt;

    /** \u5f53\u524d\u5bf9\u8be5\u8bb0\u5f55\u6301\u6709\u6295\u9012\u79df\u7ea6\u7684\u6d3e\u53d1\u5668\u6807\u8bc6\u3002 */
    private final String leaseOwner;

    /** \u5f53\u524d\u72ec\u5360\u6295\u9012\u79df\u7ea6\u7684\u5931\u6548\u65f6\u95f4\u3002 */
    private final Instant leaseUntil;

    /** \u5f53\u524d Outbox \u8bb0\u5f55\u7d2f\u8ba1\u5c1d\u8bd5\u7684\u53d1\u9001\u6b21\u6570\u3002 */
    private final int attempts;

    /** \u4e0a\u6b21\u6295\u9012\u5931\u8d25\u7684\u8bca\u65ad\u8bf4\u660e\u3002 */
    private final String lastError;

    /** \u6b64\u8bb0\u5f55\u6210\u529f\u6807\u8bb0\u4e3a\u53d1\u5e03\u5b8c\u6210\u7684\u65f6\u95f4\u3002 */
    private final Instant publishedAt;

    /**
     * \u53ef\u6301\u4e45\u5316\u7684 Outbox \u6d88\u606f\u5feb\u7167\u3002
     *
     *  <p>\u8be5\u5bf9\u8c61\u4e0d\u7ed1\u5b9a JSON\u3001\u6570\u636e\u5e93\u6216 broker\uff1b\u5b58\u50a8\u9002\u914d\u5668\u8d1f\u8d23\u5c06\u5176\u6620\u5c04\u4e3a\u81ea\u5df1\u7684\u8868\u7ed3\u6784\u3002
     * @param messageId \u6d88\u606f\u6807\u8bc6
     * @param destination \u76ee\u6807\u5bf9\u8c61
     * @param payload \u6d88\u606f\u8d1f\u8f7d
     * @param headers \u6d88\u606f\u5934\u96c6\u5408
     * @param status \u72b6\u6001
     * @param availableAt \u53c2\u6570 availableAt
     * @param leaseOwner \u5b57\u7b26\u4e32\u53c2\u6570
     * @param leaseUntil \u53c2\u6570 leaseUntil
     * @param attempts \u6574\u578b\u6570\u503c
     * @param lastError \u672b\u4e2a\u9519\u8bef
     * @param publishedAt \u53c2\u6570 publishedAt
     */
    @JsonCreator()
    public MQOutboxRecord(@JsonProperty("messageId") String messageId, @JsonProperty("destination") String destination, @JsonProperty("payload") String payload, @JsonProperty("headers") Map<String, String> headers, @JsonProperty("status") MQOutboxStatus status, @JsonProperty("availableAt") Instant availableAt, @JsonProperty("leaseOwner") String leaseOwner, @JsonProperty("leaseUntil") Instant leaseUntil, @JsonProperty("attempts") int attempts, @JsonProperty("lastError") String lastError, @JsonProperty("publishedAt") Instant publishedAt) {
        if (StrKit.isBlank(messageId)) {
            throw new IllegalArgumentException("messageId must not be blank");
        }
        if (StrKit.isBlank(destination)) {
            throw new IllegalArgumentException("destination must not be blank");
        }
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(headers, "headers must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(availableAt, "availableAt must not be null");
        if (attempts < 0) {
            throw new IllegalArgumentException("attempts must not be negative");
        }
        Map<String, String> mutableHeaders = new LinkedHashMap<>(headers);
        mutableHeaders.put(MQDeliveryHeaders.MESSAGE_ID, messageId);
        headers = Map.copyOf(mutableHeaders);
        this.messageId = messageId;
        this.destination = destination;
        this.payload = payload;
        this.headers = headers;
        this.status = status;
        this.availableAt = availableAt;
        this.leaseOwner = leaseOwner;
        this.leaseUntil = leaseUntil;
        this.attempts = attempts;
        this.lastError = lastError;
        this.publishedAt = publishedAt;
    }

    /**
     * 创建一条等待发布的消息。
     *
     * @param messageId   稳定消息标识
     * @param destination broker 目的地
     * @param payload     已序列化事件负载
     * @param headers     业务消息头
     * @param availableAt 首次可投递时间
     * @return 待发布记录
     */
    public static MQOutboxRecord pending(String messageId, String destination, String payload, Map<String, String> headers, Instant availableAt) {
        return new MQOutboxRecord(messageId, destination, payload, headers, MQOutboxStatus.PENDING, availableAt, null, null, 0, null, null);
    }

    /** @return \u7a33\u5b9a\u6d88\u606f\u6807\u8bc6 */
    @JsonProperty("messageId")
    public String messageId() {
        return messageId;
    }

    /** @return Broker \u76ee\u7684\u5730 */
    @JsonProperty("destination")
    public String destination() {
        return destination;
    }

    /** @return \u5df2\u5e8f\u5217\u5316\u4e8b\u4ef6\u8d1f\u8f7d */
    @JsonProperty("payload")
    public String payload() {
        return payload;
    }

    /** @return \u4e0d\u53ef\u4fee\u6539\u7684\u6d88\u606f\u5934\u5feb\u7167 */
    @JsonProperty("headers")
    public Map<String, String> headers() {
        return headers;
    }

    /** @return \u5f53\u524d\u6295\u9012\u72b6\u6001 */
    @JsonProperty("status")
    public MQOutboxStatus status() {
        return status;
    }

    /** @return \u4e0b\u4e00\u6b21\u53ef\u6295\u9012\u65f6\u95f4 */
    @JsonProperty("availableAt")
    public Instant availableAt() {
        return availableAt;
    }

    /** @return \u5f53\u524d\u79df\u7ea6\u6301\u6709\u8005 */
    @JsonProperty("leaseOwner")
    public String leaseOwner() {
        return leaseOwner;
    }

    /** @return \u5f53\u524d\u79df\u7ea6\u622a\u6b62\u65f6\u95f4 */
    @JsonProperty("leaseUntil")
    public Instant leaseUntil() {
        return leaseUntil;
    }

    /** @return \u5df2\u5c1d\u8bd5\u6295\u9012\u6b21\u6570 */
    @JsonProperty("attempts")
    public int attempts() {
        return attempts;
    }

    /** @return \u6700\u8fd1\u4e00\u6b21\u6295\u9012\u9519\u8bef */
    @JsonProperty("lastError")
    public String lastError() {
        return lastError;
    }

    /** @return \u786e\u8ba4\u53d1\u5e03\u7684\u65f6\u95f4 */
    @JsonProperty("publishedAt")
    public Instant publishedAt() {
        return publishedAt;
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
        MQOutboxRecord other = (MQOutboxRecord) obj;
        return Objects.equals(this.messageId, other.messageId) && Objects.equals(this.destination, other.destination) && Objects.equals(this.payload, other.payload) && Objects.equals(this.headers, other.headers) && Objects.equals(this.status, other.status) && Objects.equals(this.availableAt, other.availableAt) && Objects.equals(this.leaseOwner, other.leaseOwner) && Objects.equals(this.leaseUntil, other.leaseUntil) && this.attempts == other.attempts && Objects.equals(this.lastError, other.lastError) && Objects.equals(this.publishedAt, other.publishedAt);
    }

    /**
     * 计算与对象相等条件一致的哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(messageId);
        result = 31 * result + Objects.hashCode(destination);
        result = 31 * result + Objects.hashCode(payload);
        result = 31 * result + Objects.hashCode(headers);
        result = 31 * result + Objects.hashCode(status);
        result = 31 * result + Objects.hashCode(availableAt);
        result = 31 * result + Objects.hashCode(leaseOwner);
        result = 31 * result + Objects.hashCode(leaseUntil);
        result = 31 * result + Integer.hashCode(attempts);
        result = 31 * result + Objects.hashCode(lastError);
        result = 31 * result + Objects.hashCode(publishedAt);
        return result;
    }

    /**
     * 生成用于调试和日志的对象描述，包含<code>messageId</code>、<code>destination</code>、<code>payload</code>、<code>headers</code>、<code>status</code>、<code>availableAt</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "MQOutboxRecord[messageId=" + messageId + ", destination=" + destination + ", payload=" + payload + ", headers=" + headers + ", status=" + status + ", availableAt=" + availableAt + ", leaseOwner=" + leaseOwner + ", leaseUntil=" + leaseUntil + ", attempts=" + attempts + ", lastError=" + lastError + ", publishedAt=" + publishedAt + "]";
    }
}
