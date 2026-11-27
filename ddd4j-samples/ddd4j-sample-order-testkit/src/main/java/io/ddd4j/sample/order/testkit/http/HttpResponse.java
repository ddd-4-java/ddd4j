package io.ddd4j.sample.order.testkit.http;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** 保存响应状态、响应头和已解码正文，供回归测试读取。 */
public final class HttpResponse<T> {
    /** 发起请求所使用的目标 URI。 */
    private final URI uri;
    /** 调用方收到的业务状态编码。 */
    private final int code;
    /** HTTP 消息的头部字段。 */
    private final Headers headers;
    /** HTTP 请求或响应携带的正文。 */
    private final T body;
    /** 保存 <code>version</code> 对应的 <code>String</code> 数据。 */
    private final String version;

    /**
     * 将服务器返回的状态、头部和正文封装为示例 HTTP 响应。
     *
     * @param uri 请求目标 URI
     * @param code 随响应返回的业务状态编码
     * @param headers 此 HTTP 消息的头部字段
     * @param body 此 HTTP 消息携带的正文
     * @param version 传入此操作的 <code>String</code> 类型 <code>version</code>
     */
    HttpResponse(URI uri, int code, Header[] headers, T body, String version) {
        this.uri = uri;
        this.code = code;
        this.headers = new Headers(headers);
        this.body = body;
        this.version = version;
    }

    /**
     * 读取发起请求所使用的目标 URI。
     *
     * @return 返回表达式 <code>uri</code>
     */
    public URI uri() { return uri; }

    /**
     * 读取调用方收到的业务状态编码。
     *
     * @return 返回表达式 <code>code</code>
     */
    public int statusCode() { return code; }

    /**
     * 读取HTTP 请求或响应携带的正文。
     *
     * @return 返回表达式 <code>body</code>
     */
    public T body() { return body; }

    /**
     * 读取<code>version</code> 的当前值。
     *
     * @return 返回表达式 <code>version</code>
     */
    public String version() { return version; }

    /**
     * 读取HTTP 消息的头部字段。
     *
     * @return 返回表达式 <code>headers</code>
     */
    public Headers headers() { return headers; }

    /** 集中保存 <code>values</code>，供当前模块使用。 */
    public static final class Headers {
        /** 保存 <code>values</code> 对应的 <code>Map&lt;String, List&lt;String&gt;&gt;</code> 数据。 */
        private final Map<String, List<String>> values = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        /**
         * 初始化 <code>Headers</code> 并委托其默认构造路径。
         *
         * @param headers 此 HTTP 消息的头部字段
         */
        private Headers(Header[] headers) {
            for (Header header : headers) {
                values.computeIfAbsent(header.getName(), key -> new ArrayList<>()).add(header.getValue());
            }
        }

        /**
         * 执行 <code>first Value</code>，调用 <code>values.get</code>、<code>Optional.empty</code>、<code>Optional.of</code>。
         *
         * @param name 传入此操作的 <code>String</code> 类型 <code>name</code>
         * @return 返回表达式 <code>Objects.isNull(header) ? Optional.empty() : Optional.of(header.get(0))</code>
         */
        public Optional<String> firstValue(String name) {
            List<String> header = values.get(name);
            return Objects.isNull(header) ? Optional.empty() : Optional.of(header.get(0));
        }

        /**
         * 读取<code>values</code> 的当前值。
         *
         * @return 返回表达式 <code>values</code>
         */
        public Map<String, List<String>> map() {
            return values;
        }
    }

    /** 集中保存 <code>BodyHandler</code> 的声明，供当前模块使用。 */
    public interface BodyHandler<T> {
        /**
         * 执行 <code>read</code> 对应的处理。
         *
         * @param entity 传入此操作的 <code>org.apache.hc.core5.http.HttpEntity</code> 类型 <code>entity</code>
         * @return 当前操作得到的 <code>T</code> 结果
         * @throws IOException 读取或写入测试数据时发生 I/O 错误
         */
        T read(org.apache.hc.core5.http.HttpEntity entity) throws IOException;
    }

    /** 集中保存 <code>BodyHandlers</code> 的声明，供当前模块使用。 */
    public static final class BodyHandlers {
        /** 初始化 <code>BodyHandlers</code> 并委托其默认构造路径。 */
        private BodyHandlers() {
        }

        /**
         * 执行 <code>of String</code>，调用 <code>EntityUtils.toString</code>。
         *
         * @return 返回表达式 <code>EntityUtils.toString(entity, StandardCharsets.UTF_8)</code>
         */
        public static BodyHandler<String> ofString() {
            return entity -> {
                if (Objects.isNull(entity)) {
                    return "";
                }
                try {
                    return EntityUtils.toString(entity, StandardCharsets.UTF_8);
                } catch (org.apache.hc.core5.http.ParseException failure) {
                    throw new IOException(failure);
                }
            };
        }
    }
}
