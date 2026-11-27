package io.ddd4j.web.core.idempotency;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.time.Duration;
import java.util.Objects;

/**
 * 一次幂等请求获取到的租约。
 *
 * <p>ownerToken 用于保证过期请求不能完成或释放后续请求重新获取的同一个幂等键。
 * 调用方只应将实例交回创建它的 {@link IdempotencyGuard}。
 */

public final class IdempotencyLease {

    /** Java 序列化版本标识。 */
    private static final long serialVersionUID = 0L;

    /** \u6807\u8bc6\u5f53\u524d\u5e42\u7b49\u8bb0\u5f55\u7684\u4e3b\u952e\u3002 */
    private final String key;

    /** \u533a\u5206\u5f53\u524d\u5e42\u7b49\u79df\u7ea6\u4e0e\u5176\u4ed6\u8bf7\u6c42\u6301\u6709\u6743\u7684\u4ee4\u724c\u3002 */
    private final String ownerToken;

    /** \u5e42\u7b49\u8bb0\u5f55\u4fdd\u7559\u7684\u65f6\u957f\u3002 */
    private final Duration ttl;

    /**
     * 初始化 <code>IdempotencyLease</code> 使用的 <code>key</code>、<code>ownerToken</code>、<code>ttl</code>。
     *
     * @param key \u5b58\u50a8\u952e
     * @param ownerToken \u6b64\u6b21\u83b7\u53d6\u7684\u552f\u4e00\u6240\u6709\u8005\u6807\u8bc6
     * @param ttl \u79df\u7ea6\u6709\u6548\u671f
     */

    @JsonCreator()
    public IdempotencyLease(@JsonProperty("key") String key, @JsonProperty("ownerToken") String ownerToken, @JsonProperty("ttl") Duration ttl) {
        key = Objects.requireNonNull(key, "key must not be null");
        ttl = Objects.requireNonNull(ttl, "ttl must not be null");
        this.key = key;
        this.ownerToken = ownerToken;
        this.ttl = ttl;
    }

    /**
     * \u8fd4\u56de\u5b58\u50a8\u952e\u3002
     * @return \u5b58\u50a8\u952e
     */
    @JsonProperty("key")
    public String key() {
        return key;
    }

    /**
     * \u8fd4\u56de\u79df\u7ea6\u6240\u6709\u8005\u4ee4\u724c\u3002
     * @return \u6240\u6709\u8005\u4ee4\u724c
     */
    @JsonProperty("ownerToken")
    public String ownerToken() {
        return ownerToken;
    }

    /**
     * \u8fd4\u56de\u79df\u7ea6\u6709\u6548\u671f\u3002
     * @return \u6709\u6548\u671f
     */
    @JsonProperty("ttl")
    public Duration ttl() {
        return ttl;
    }

    /**
     * 根据<code>key</code>、<code>ownerToken</code>、<code>ttl</code>比较对象的值是否相等。
     *
     * <p>只比较运行时类型相同的对象。</p>
     *
     * @param obj 用来比较或转换的输入对象
     * @return 对象满足此实现的相等条件时为真
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        IdempotencyLease other = (IdempotencyLease) obj;
        return Objects.equals(this.key, other.key) && Objects.equals(this.ownerToken, other.ownerToken) && Objects.equals(this.ttl, other.ttl);
    }

    /**
     * 计算与对象相等条件一致的哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(key);
        result = 31 * result + Objects.hashCode(ownerToken);
        result = 31 * result + Objects.hashCode(ttl);
        return result;
    }

    /**
     * 生成用于调试和日志的对象描述，包含<code>key</code>、<code>ownerToken</code>、<code>ttl</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "IdempotencyLease[key=" + key + ", ownerToken=" + ownerToken + ", ttl=" + ttl + "]";
    }
}
