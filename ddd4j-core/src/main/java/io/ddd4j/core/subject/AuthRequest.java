package io.ddd4j.core.subject;

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
 * @since 3.4.x
 */
public class AuthRequest {

    /** 账号 ID（必填） */
    private Object loginId;

    /** 认证主体（可选，登录后通过 {@link Subject#getPrincipal()} 取回） */
    private AuthPrincipal principal;

    /** 会话有效期（秒），-1 表示永久 */
    private long timeout = -1;

    /** 设备类型（多端登录隔离用） */
    private String deviceType;

    /** 多账号体系标识（如 sa-token 的 "admin"/"user"） */
    private String realm;

    /** 扩展信息（写入 Token Claim 或 Session） */
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

    public Object getLoginId() {
        return loginId;
    }

    public String getLoginIdAsString() {
        return loginId == null ? null : String.valueOf(loginId);
    }

    public void setLoginId(Object loginId) {
        this.loginId = loginId;
    }

    public AuthPrincipal getPrincipal() {
        return principal;
    }

    public AuthRequest setPrincipal(AuthPrincipal principal) {
        this.principal = principal;
        return this;
    }

    public long getTimeout() {
        return timeout;
    }

    public AuthRequest setTimeout(long timeout) {
        this.timeout = timeout;
        return this;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public AuthRequest setDeviceType(String deviceType) {
        this.deviceType = deviceType;
        return this;
    }

    public String getRealm() {
        return realm;
    }

    public AuthRequest setRealm(String realm) {
        this.realm = realm;
        return this;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public AuthRequest setExtra(Map<String, Object> extra) {
        this.extra = extra;
        return this;
    }

    /**
     * 追加扩展信息。
     *
     * @param key   键
     * @param value 值
     * @return 当前对象（链式调用）
     */
    public AuthRequest extra(String key, Object value) {
        this.extra.put(key, value);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
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
