package io.ddd4j.web.core.error;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import io.ddd4j.core.api.R;

import java.io.Serializable;

/**
 * HTTP 状态与 ddd4j 响应体之间的统一错误表示。
 */
public final class WebError {

    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    private static final long serialVersionUID = 0L;

    /** \u5bf9\u5e94 HTTP \u9519\u8bef\u54cd\u5e94\u7684\u72b6\u6001\u7801\u3002 */
    private final int status;

    /** \u54cd\u5e94\u4f53\u4e2d\u62a5\u544a\u7684\u4e1a\u52a1\u9519\u8bef\u7801\u3002 */
    private final Serializable code;

    /** \u54cd\u5e94\u4f53\u4e2d\u4f9b\u8c03\u7528\u65b9\u8bfb\u53d6\u7684\u9519\u8bef\u8bf4\u660e\u3002 */
    private final String message;

    /** \u9519\u8bef\u54cd\u5e94\u4e2d\u968f\u72b6\u6001\u8fd4\u56de\u7684\u53ef\u9009\u4e1a\u52a1\u6570\u636e\u3002 */
    private final Object data;

    /**
     * \u5c06\u9519\u8bef\u8f6c\u6362\u4e3a\u7edf\u4e00\u54cd\u5e94\u5bf9\u8c61\u3002
     * @return \u7edf\u4e00\u9519\u8bef\u54cd\u5e94
     */
    public R<Object> toResponse() {
        return R.fail(code, message, data);
    }

    /**
     * \u521b\u5efa Web \u9519\u8bef\u503c\u5bf9\u8c61\u3002
     * @param status HTTP \u72b6\u6001\u7801
     * @param code \u4e1a\u52a1\u9519\u8bef\u7801
     * @param message \u9519\u8bef\u6d88\u606f
     * @param data \u9519\u8bef\u6570\u636e
     */
    @JsonCreator()
    public WebError(@JsonProperty("status") int status, @JsonProperty("code") Serializable code, @JsonProperty("message") String message, @JsonProperty("data") Object data) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * \u8fd4\u56deHTTP \u72b6\u6001\u7801\u3002
     * @return HTTP \u72b6\u6001\u7801
     */
    @JsonProperty("status")
    public int status() {
        return status;
    }

    /**
     * \u8fd4\u56de\u9519\u8bef\u7801\u3002
     * @return \u9519\u8bef\u7801
     */
    @JsonProperty("code")
    public Serializable code() {
        return code;
    }

    /**
     * \u8fd4\u56de\u9519\u8bef\u6d88\u606f\u3002
     * @return \u9519\u8bef\u6d88\u606f
     */
    @JsonProperty("message")
    public String message() {
        return message;
    }

    /**
     * \u8fd4\u56de\u9519\u8bef\u6570\u636e\u3002
     * @return \u9519\u8bef\u6570\u636e
     */
    @JsonProperty("data")
    public Object data() {
        return data;
    }

    /**
     * 根据<code>code</code>、<code>message</code>、<code>data</code>、<code>status</code>比较对象的值是否相等。
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
        WebError other = (WebError) obj;
        return this.status == other.status && Objects.equals(this.code, other.code) && Objects.equals(this.message, other.message) && Objects.equals(this.data, other.data);
    }

    /**
     * 计算与对象相等条件一致的哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Integer.hashCode(status);
        result = 31 * result + Objects.hashCode(code);
        result = 31 * result + Objects.hashCode(message);
        result = 31 * result + Objects.hashCode(data);
        return result;
    }

    /**
     * 生成用于调试和日志的对象描述，包含<code>status</code>、<code>code</code>、<code>message</code>、<code>data</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "WebError[status=" + status + ", code=" + code + ", message=" + message + ", data=" + data + "]";
    }
}
