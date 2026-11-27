package io.ddd4j.web.core.context;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

/**
 * 可由运行时事件总线观测的框架无关 HTTP 请求失败事件。
 */
public final class WebRequestFailure {

    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    private static final long serialVersionUID = 0L;

    /** \u5f53\u524d HTTP \u8bf7\u6c42\u7684\u65b9\u6cd5\u3002 */
    private final String method;

    /** \u5f53\u524d\u6846\u67b6\u89e3\u6790\u51fa\u7684\u8bf7\u6c42\u8def\u5f84\u3002 */
    private final String path;

    /** \u5bfc\u81f4\u5f53\u524d\u8bf7\u6c42\u65e0\u6cd5\u6b63\u5e38\u5b8c\u6210\u7684\u539f\u59cb\u5f02\u5e38\u3002 */
    private final Throwable cause;

    /**
     * \u521b\u5efa\u8bf7\u6c42\u5931\u8d25\u8bb0\u5f55\u3002
     * @param method HTTP \u65b9\u6cd5
     * @param path \u8bf7\u6c42\u8def\u5f84
     * @param cause \u539f\u59cb\u5f02\u5e38
     */
    @JsonCreator()
    public WebRequestFailure(@JsonProperty("method") String method, @JsonProperty("path") String path, @JsonProperty("cause") Throwable cause) {
        this.method = method;
        this.path = path;
        this.cause = cause;
    }

    /**
     * \u8fd4\u56deHTTP \u65b9\u6cd5\u3002
     * @return HTTP \u65b9\u6cd5
     */
    @JsonProperty("method")
    public String method() {
        return method;
    }

    /**
     * \u8fd4\u56de\u8bf7\u6c42\u8def\u5f84\u3002
     * @return \u8bf7\u6c42\u8def\u5f84
     */
    @JsonProperty("path")
    public String path() {
        return path;
    }

    /**
     * \u8fd4\u56de\u5f02\u5e38\u539f\u56e0\u3002
     * @return \u5f02\u5e38\u539f\u56e0
     */
    @JsonProperty("cause")
    public Throwable cause() {
        return cause;
    }

    /**
     * 根据<code>method</code>、<code>path</code>、<code>cause</code>比较对象的值是否相等。
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
        WebRequestFailure other = (WebRequestFailure) obj;
        return Objects.equals(this.method, other.method) && Objects.equals(this.path, other.path) && Objects.equals(this.cause, other.cause);
    }

    /**
     * 计算与对象相等条件一致的哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(method);
        result = 31 * result + Objects.hashCode(path);
        result = 31 * result + Objects.hashCode(cause);
        return result;
    }

    /**
     * 生成用于调试和日志的对象描述，包含<code>method</code>、<code>path</code>、<code>cause</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "WebRequestFailure[method=" + method + ", path=" + path + ", cause=" + cause + "]";
    }
}
