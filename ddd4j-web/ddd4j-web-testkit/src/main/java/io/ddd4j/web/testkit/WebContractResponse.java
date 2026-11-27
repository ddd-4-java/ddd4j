package io.ddd4j.web.testkit;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Web 契约测试使用的最小响应快照。
 */
public final class WebContractResponse {

    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    private static final long serialVersionUID = 0L;

    /** \u8981\u5199\u5165 HTTP \u54cd\u5e94\u7684\u72b6\u6001\u7801\u3002 */
    private final int status;

    /** HTTP \u5408\u540c\u8bb0\u5f55\u7684\u54cd\u5e94\u5934\u6620\u5c04\u3002 */
    private final Map<String, List<String>> headers;

    /** HTTP \u5408\u540c\u8bb0\u5f55\u7684\u54cd\u5e94\u6b63\u6587\u3002 */
    private final String body;

    /**
     * \u6309\u540d\u79f0\u67e5\u8be2\u9996\u4e2a\u54cd\u5e94\u5934\u503c\uff0c\u5ffd\u7565\u540d\u79f0\u5927\u5c0f\u5199\u3002
     * @param name \u54cd\u5e94\u5934\u540d\u79f0
     * @return \u9996\u4e2a\u503c\uff1b\u4e0d\u5b58\u5728\u65f6\u4e3a\u7a7a
     */
    public Optional<String> firstHeader(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name)).map(Map.Entry::getValue).filter(values -> Objects.nonNull(values) && !values.isEmpty()).map(values -> values.get(0)).findFirst();
    }

    /**
     * \u521b\u5efa Web \u5951\u7ea6\u54cd\u5e94\u5feb\u7167\u3002
     * @param status HTTP \u72b6\u6001\u7801
     * @param headers \u54cd\u5e94\u5934
     * @param body \u54cd\u5e94\u4f53
     */
    @JsonCreator()
    public WebContractResponse(@JsonProperty("status") int status, @JsonProperty("headers") Map<String, List<String>> headers, @JsonProperty("body") String body) {
        this.status = status;
        this.headers = headers;
        this.body = body;
    }

    /** @return HTTP \u72b6\u6001\u7801 */
    @JsonProperty("status")
    public int status() {
        return status;
    }

    /** @return \u54cd\u5e94\u5934 */
    @JsonProperty("headers")
    public Map<String, List<String>> headers() {
        return headers;
    }

    /** @return \u54cd\u5e94\u4f53 */
    @JsonProperty("body")
    public String body() {
        return body;
    }

    /**
     * 根据<code>headers</code>、<code>body</code>、<code>status</code>比较对象的值是否相等。
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
        WebContractResponse other = (WebContractResponse) obj;
        return this.status == other.status && Objects.equals(this.headers, other.headers) && Objects.equals(this.body, other.body);
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
        result = 31 * result + Objects.hashCode(headers);
        result = 31 * result + Objects.hashCode(body);
        return result;
    }

    /**
     * 生成用于调试和日志的对象描述，包含<code>status</code>、<code>headers</code>、<code>body</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "WebContractResponse[status=" + status + ", headers=" + headers + ", body=" + body + "]";
    }
}
