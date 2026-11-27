package io.ddd4j.core.auth.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import io.ddd4j.core.auth.AuthRequest;

import java.time.Instant;

/**
 * 登录失败事件（通用鉴权事件）。
 *
 * <p>由具体 {@link io.ddd4j.core.subject.Subject} 实现在登录校验失败时发布。
 * 业务方可通过 {@link io.ddd4j.core.ddd.event.DomainEventPublisher} 订阅。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public final class AuthFailedEvent {

    /** Java 序列化版本标识。 */
    private static final long serialVersionUID = 0L;

    /** \u83b7\u53d6\u767b\u5f55\u8bf7\u6c42\u3002 */
    private final AuthRequest request;

    /** \u83b7\u53d6\u767b\u5f55\u5931\u8d25\u539f\u56e0\u3002 */
    private final String reason;

    /** \u83b7\u53d6\u4e8b\u4ef6\u53d1\u751f\u65f6\u95f4\u3002 */
    private final Instant occurredAt;

    /**
     * \u521b\u5efaAuthFailedEvent\u4e8b\u4ef6\u3002
     * @param request \u767b\u5f55\u8bf7\u6c42
     * @param reason \u767b\u5f55\u5931\u8d25\u539f\u56e0
     * @param occurredAt \u4e8b\u4ef6\u53d1\u751f\u65f6\u95f4
     */
    @JsonCreator()
    public AuthFailedEvent(@JsonProperty("request") AuthRequest request, @JsonProperty("reason") String reason, @JsonProperty("occurredAt") Instant occurredAt) {
        this.request = request;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    /**
     * \u83b7\u53d6\u7528\u4e8e JSON \u7684\u767b\u5f55\u8bf7\u6c42\u3002
     * @return \u7528\u4e8e JSON \u7684\u767b\u5f55\u8bf7\u6c42
     */
    @JsonProperty("request")
    public AuthRequest request() {
        return request;
    }

    /**
     * \u83b7\u53d6\u7528\u4e8e JSON \u7684\u767b\u5f55\u5931\u8d25\u539f\u56e0\u3002
     * @return \u7528\u4e8e JSON \u7684\u767b\u5f55\u5931\u8d25\u539f\u56e0
     */
    @JsonProperty("reason")
    public String reason() {
        return reason;
    }

    /**
     * \u83b7\u53d6\u7528\u4e8e JSON \u7684\u4e8b\u4ef6\u53d1\u751f\u65f6\u95f4\u3002
     * @return \u7528\u4e8e JSON \u7684\u4e8b\u4ef6\u53d1\u751f\u65f6\u95f4
     */
    @JsonProperty("occurredAt")
    public Instant occurredAt() {
        return occurredAt;
    }

    /**
     * 根据<code>request</code>、<code>reason</code>、<code>occurredAt</code>比较对象的值是否相等。
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
        AuthFailedEvent other = (AuthFailedEvent) obj;
        return Objects.equals(this.request, other.request) && Objects.equals(this.reason, other.reason) && Objects.equals(this.occurredAt, other.occurredAt);
    }

    /**
     * 计算与对象相等条件一致的哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(request);
        result = 31 * result + Objects.hashCode(reason);
        result = 31 * result + Objects.hashCode(occurredAt);
        return result;
    }

    /**
     * 生成用于调试和日志的对象描述，包含<code>request</code>、<code>reason</code>、<code>occurredAt</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "AuthFailedEvent[request=" + request + ", reason=" + reason + ", occurredAt=" + occurredAt + "]";
    }
}
