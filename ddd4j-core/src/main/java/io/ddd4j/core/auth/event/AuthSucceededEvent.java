package io.ddd4j.core.auth.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;

import java.time.Instant;

/**
 * 登录成功事件（通用鉴权事件）。
 *
 * <p>由具体 {@link io.ddd4j.core.subject.Subject} 实现在建立会话时发布。
 * 业务方可通过 {@link io.ddd4j.core.ddd.event.DomainEventPublisher} 订阅。
 *
 * <p>各框架适配层应负责把 ddd4j 通用事件桥接到本地事件总线：
 * <ul>
 *   <li>Spring：{@code SpringDomainEventPublisher} 解包 DomainEvent 后 publishEvent</li>
 *   <li>Quarkus：CDI {@code Event<LoginSucceededEvent>}</li>
 *   <li>Guice：Guava EventBus</li>
 *   <li>Javalin：业务方自定义</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public final class AuthSucceededEvent {

    /** Java 序列化版本标识。 */
    private static final long serialVersionUID = 0L;

    /** \u83b7\u53d6\u767b\u5f55\u8bf7\u6c42\u3002 */
    private final AuthRequest request;

    /** \u83b7\u53d6\u8ba4\u8bc1\u4e3b\u4f53\u3002 */
    private final AuthPrincipal principal;

    /** \u83b7\u53d6\u4f1a\u8bdd\u51ed\u8bc1\u3002 */
    private final String token;

    /** \u83b7\u53d6\u4e8b\u4ef6\u53d1\u751f\u65f6\u95f4\u3002 */
    private final Instant occurredAt;

    /**
     * \u521b\u5efaAuthSucceededEvent\u4e8b\u4ef6\u3002
     * @param request \u767b\u5f55\u8bf7\u6c42
     * @param principal \u8ba4\u8bc1\u4e3b\u4f53
     * @param token \u4f1a\u8bdd\u51ed\u8bc1
     * @param occurredAt \u4e8b\u4ef6\u53d1\u751f\u65f6\u95f4
     */
    @JsonCreator()
    public AuthSucceededEvent(@JsonProperty("request") AuthRequest request, @JsonProperty("principal") AuthPrincipal principal, @JsonProperty("token") String token, @JsonProperty("occurredAt") Instant occurredAt) {
        this.request = request;
        this.principal = principal;
        this.token = token;
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
     * \u83b7\u53d6\u7528\u4e8e JSON \u7684\u8ba4\u8bc1\u4e3b\u4f53\u3002
     * @return \u7528\u4e8e JSON \u7684\u8ba4\u8bc1\u4e3b\u4f53
     */
    @JsonProperty("principal")
    public AuthPrincipal principal() {
        return principal;
    }

    /**
     * \u83b7\u53d6\u7528\u4e8e JSON \u7684\u4f1a\u8bdd\u51ed\u8bc1\u3002
     * @return \u7528\u4e8e JSON \u7684\u4f1a\u8bdd\u51ed\u8bc1
     */
    @JsonProperty("token")
    public String token() {
        return token;
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
     * 根据<code>request</code>、<code>principal</code>、<code>token</code>、<code>occurredAt</code>比较对象的值是否相等。
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
        AuthSucceededEvent other = (AuthSucceededEvent) obj;
        return Objects.equals(this.request, other.request) && Objects.equals(this.principal, other.principal) && Objects.equals(this.token, other.token) && Objects.equals(this.occurredAt, other.occurredAt);
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
        result = 31 * result + Objects.hashCode(principal);
        result = 31 * result + Objects.hashCode(token);
        result = 31 * result + Objects.hashCode(occurredAt);
        return result;
    }

    /**
     * 生成用于调试和日志的对象描述，包含<code>request</code>、<code>principal</code>、<code>token</code>、<code>occurredAt</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "AuthSucceededEvent[request=" + request + ", principal=" + principal + ", token=" + token + ", occurredAt=" + occurredAt + "]";
    }
}
