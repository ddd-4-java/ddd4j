package io.ddd4j.core.auth;

import io.ddd4j.core.subject.Subject;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 登录请求载体（纯 Java 值对象）。
 *
 * <p>承载登录所需的全部信息，由业务构造，由 {@link Subject} 实现消费。
 * 对齐 Sa-Token 的 {@code SaLoginParameter}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Data
@Accessors(chain = true)
public class AuthRequest {

    /**
     * 账号 ID（必填）
     */
    private Object loginId;

    /**
     * 认证主体（可选，登录后通过 {@link Subject#getPrincipal()} 取回）
     */
    private AuthPrincipal principal;

    /**
     * 会话有效期（秒），-1 表示永久
     */
    private long timeout = -1;

    /**
     * 设备类型（多端登录隔离用）
     */
    private String deviceType;

    /**
     * 多账号体系标识（如 sa-token 的 "admin"/"user"）
     */
    private String realm;

    /**
     * 扩展信息（写入 Token Claim 或 Session）
     */
    private Map<String, Object> extra = new HashMap<>();

    public AuthRequest() {
    }

    public AuthRequest(Object loginId) {
        this.loginId = loginId;
    }

    /**
     * 快速构造登录请求。
     *
     * @param loginId 账号 ID
     * @return 登录请求
     */
    public static AuthRequest of(Object loginId) {
        return new AuthRequest(loginId);
    }

    public String getLoginIdAsString() {
        return Objects.isNull(loginId) ? null : String.valueOf(loginId);
    }

    public AuthRequest extra(String key, Object value) {
        if (Objects.isNull(extra)) {
            extra = new HashMap<>();
        }
        extra.put(Objects.requireNonNull(key, "key must not be null"), value);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (Objects.isNull(o) || getClass() != o.getClass()) {
            return false;
        }
        AuthRequest that = (AuthRequest) o;
        return Objects.equals(loginId, that.loginId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(loginId);
    }

    @Override
    public String toString() {
        return "AuthRequest{loginId=" + loginId + ", realm='" + realm + "', deviceType='" + deviceType + "', timeout=" + timeout + "}";
    }

}
