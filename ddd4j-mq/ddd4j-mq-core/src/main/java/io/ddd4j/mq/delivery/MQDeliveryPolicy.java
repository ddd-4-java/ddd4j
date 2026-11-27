package io.ddd4j.mq.delivery;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 可靠消息的租约和退避策略。
 *
 * <p>默认值采用 60 秒租约、12 次最多尝试，以及 1 秒到 5 分钟的指数退避。
 */
public final class MQDeliveryPolicy {

    /** Java 序列化版本标识。 */
    private static final long serialVersionUID = 0L;

    /** \u8ba4\u9886\u6295\u9012\u8bb0\u5f55\u65f6\u6388\u4e88\u7684\u72ec\u5360\u79df\u7ea6\u65f6\u957f\u3002 */
    private final Duration leaseDuration;

    /** \u6295\u9012\u6700\u591a\u5141\u8bb8\u6267\u884c\u7684\u6b21\u6570\u3002 */
    private final int maxAttempts;

    /** \u8ba1\u7b97\u91cd\u8bd5\u9000\u907f\u65f6\u4f7f\u7528\u7684\u57fa\u7840\u65f6\u957f\u3002 */
    private final Duration initialBackoff;

    /** \u5355\u6b21\u91cd\u8bd5\u9000\u907f\u7684\u65f6\u957f\u4e0a\u9650\u3002 */
    private final Duration maxBackoff;

    /** \u5728\u57fa\u7840\u9000\u907f\u65f6\u95f4\u4e0a\u5e94\u7528\u7684\u6296\u52a8\u7cfb\u6570\u3002 */
    private final double jitterFactor;

    /** \u9ed8\u8ba4\u6295\u9012\u4efb\u52a1\u79df\u7ea6\u65f6\u957f\u3002 */
    private static final Duration DEFAULT_LEASE_DURATION = Duration.ofSeconds(60);

    /** \u9996\u6b21\u5931\u8d25\u540e\u7b49\u5f85\u91cd\u8bd5\u7684\u9ed8\u8ba4\u65f6\u957f\u3002 */
    private static final Duration DEFAULT_INITIAL_BACKOFF = Duration.ofSeconds(1);

    /** \u91cd\u8bd5\u9000\u907f\u65f6\u95f4\u9ed8\u8ba4\u4e0a\u9650\u3002 */
    private static final Duration DEFAULT_MAX_BACKOFF = Duration.ofMinutes(5);

    /** \u9ed8\u8ba4\u5141\u8bb8\u5c1d\u8bd5\u6295\u9012\u7684\u6700\u5927\u6b21\u6570\u3002 */
    private static final int DEFAULT_MAX_ATTEMPTS = 12;

    /** \u9ed8\u8ba4\u91cd\u8bd5\u6296\u52a8\u6bd4\u4f8b\u3002 */
    private static final double DEFAULT_JITTER_FACTOR = 0.20D;

    /**
     * \u53ef\u9760\u6d88\u606f\u7684\u79df\u7ea6\u548c\u9000\u907f\u7b56\u7565\u3002
     *
     *  <p>\u9ed8\u8ba4\u503c\u91c7\u7528 60 \u79d2\u79df\u7ea6\u300112 \u6b21\u6700\u591a\u5c1d\u8bd5\uff0c\u4ee5\u53ca 1 \u79d2\u5230 5 \u5206\u949f\u7684\u6307\u6570\u9000\u907f\u3002
     * @param leaseDuration \u53c2\u6570 leaseDuration
     * @param maxAttempts \u6574\u578b\u6570\u503c
     * @param initialBackoff \u53c2\u6570 initialBackoff
     * @param maxBackoff \u6700\u5927\u76f8\u5173\u53c2\u6570
     * @param jitterFactor \u53cc\u7cbe\u5ea6\u6570\u503c
     */
    @JsonCreator()
    public MQDeliveryPolicy(@JsonProperty("leaseDuration") Duration leaseDuration, @JsonProperty("maxAttempts") int maxAttempts, @JsonProperty("initialBackoff") Duration initialBackoff, @JsonProperty("maxBackoff") Duration maxBackoff, @JsonProperty("jitterFactor") double jitterFactor) {
        Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");
        Objects.requireNonNull(initialBackoff, "initialBackoff must not be null");
        Objects.requireNonNull(maxBackoff, "maxBackoff must not be null");
        if (leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be greater than zero");
        }
        if (initialBackoff.isZero() || initialBackoff.isNegative()) {
            throw new IllegalArgumentException("initialBackoff must be positive");
        }
        if (maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("maxBackoff must not be less than initialBackoff");
        }
        if (jitterFactor < 0.0D || jitterFactor > 1.0D) {
            throw new IllegalArgumentException("jitterFactor must be between zero and one");
        }
        this.leaseDuration = leaseDuration;
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
        this.jitterFactor = jitterFactor;
    }

    /**
     * 返回生产环境默认投递策略。
     *
     * @return 默认策略
     */
    public static MQDeliveryPolicy productionDefault() {
        return new MQDeliveryPolicy(DEFAULT_LEASE_DURATION, DEFAULT_MAX_ATTEMPTS, DEFAULT_INITIAL_BACKOFF, DEFAULT_MAX_BACKOFF, DEFAULT_JITTER_FACTOR);
    }

    /**
     * 计算某次失败后的下一次可投递时间。
     *
     * @param attempts           已完成的发送尝试次数，从 1 开始
     * @param failedAt           失败发生时间
     * @param randomUnitInterval [0, 1] 的随机值，由调用方提供以保持测试可重复
     * @return 带抖动的下一次可投递时间
     */
    public Instant nextAvailableAt(int attempts, Instant failedAt, double randomUnitInterval) {
        Objects.requireNonNull(failedAt, "failedAt must not be null");
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts must be greater than zero");
        }
        if (randomUnitInterval < 0.0D || randomUnitInterval > 1.0D) {
            throw new IllegalArgumentException("randomUnitInterval must be between zero and one");
        }
        long baseMillis = exponentialBackoffMillis(attempts);
        double jitter = 1.0D + ((randomUnitInterval * 2.0D - 1.0D) * jitterFactor);
        return failedAt.plusMillis(Math.round(baseMillis * jitter));
    }

    /**
     * 判断某条消息是否应进入死信状态。
     *
     * @param attempts 已完成的发送尝试次数
     * @return 是否耗尽重试次数
     */
    public boolean exhausted(int attempts) {
        return attempts >= maxAttempts;
    }

    /**
     * \u6309\u53d1\u9001\u6b21\u6570\u8ba1\u7b97\u6307\u6570\u9000\u907f\uff0c\u5e76\u5c06\u7ed3\u679c\u9650\u5236\u5728\u914d\u7f6e\u4e0a\u9650\u5185\u3002
     * @param attempts \u5f53\u524d\u7d2f\u8ba1\u53d1\u9001\u6b21\u6570
     * @return \u9000\u907f\u65f6\u95f4\uff0c\u5355\u4f4d\u4e3a\u6beb\u79d2
     */
    private long exponentialBackoffMillis(int attempts) {
        long maxMillis = maxBackoff.toMillis();
        long initialMillis = initialBackoff.toMillis();
        int exponent = Math.min(attempts - 1, 62);
        long multiplier = 1L << exponent;
        if (initialMillis > maxMillis / multiplier) {
            return maxMillis;
        }
        return Math.min(initialMillis * multiplier, maxMillis);
    }

    /** @return \u6295\u9012\u79df\u7ea6\u65f6\u957f */
    @JsonProperty("leaseDuration")
    public Duration leaseDuration() {
        return leaseDuration;
    }

    /** @return \u6700\u591a\u6295\u9012\u5c1d\u8bd5\u6b21\u6570 */
    @JsonProperty("maxAttempts")
    public int maxAttempts() {
        return maxAttempts;
    }

    /** @return \u521d\u59cb\u9000\u907f\u65f6\u957f */
    @JsonProperty("initialBackoff")
    public Duration initialBackoff() {
        return initialBackoff;
    }

    /** @return \u6700\u5927\u9000\u907f\u65f6\u957f */
    @JsonProperty("maxBackoff")
    public Duration maxBackoff() {
        return maxBackoff;
    }

    /** @return \u9000\u907f\u6296\u52a8\u7cfb\u6570 */
    @JsonProperty("jitterFactor")
    public double jitterFactor() {
        return jitterFactor;
    }

    /**
     * 执行 <code>equals</code>，调用 <code>obj.getClass</code>、<code>Double.compare</code>。
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
        MQDeliveryPolicy other = (MQDeliveryPolicy) obj;
        return Objects.equals(this.leaseDuration, other.leaseDuration) && this.maxAttempts == other.maxAttempts && Objects.equals(this.initialBackoff, other.initialBackoff) && Objects.equals(this.maxBackoff, other.maxBackoff) && Double.compare(this.jitterFactor, other.jitterFactor) == 0;
    }

    /**
     * 计算与对象相等条件一致的哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(leaseDuration);
        result = 31 * result + Integer.hashCode(maxAttempts);
        result = 31 * result + Objects.hashCode(initialBackoff);
        result = 31 * result + Objects.hashCode(maxBackoff);
        result = 31 * result + Double.hashCode(jitterFactor);
        return result;
    }

    /**
     * 生成用于调试和日志的对象描述，包含<code>leaseDuration</code>、<code>maxAttempts</code>、<code>initialBackoff</code>、<code>maxBackoff</code>、<code>jitterFactor</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "MQDeliveryPolicy[leaseDuration=" + leaseDuration + ", maxAttempts=" + maxAttempts + ", initialBackoff=" + initialBackoff + ", maxBackoff=" + maxBackoff + ", jitterFactor=" + jitterFactor + "]";
    }
}
