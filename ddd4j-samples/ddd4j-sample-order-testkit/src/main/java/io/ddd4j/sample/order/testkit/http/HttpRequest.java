package io.ddd4j.sample.order.testkit.http;

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import java.util.Objects;

/** 构造样例回归使用的 HTTP 请求。 */
public final class HttpRequest {
    /** 保存 <code>request</code> 对应的 <code>HttpUriRequestBase</code> 数据。 */
    private final HttpUriRequestBase request;

    /**
     * 初始化示例 HTTP 请求使用的方法、地址、头部或正文。
     *
     * @param request 待发送或处理的 HTTP 请求
     */
    private HttpRequest(HttpUriRequestBase request) {
        this.request = request;
    }

    /**
     * 读取<code>request</code> 的当前值。
     *
     * @return 返回表达式 <code>request</code>
     */
    HttpUriRequestBase apacheRequest() {
        return request;
    }

    /**
     * 按 <code>new Builder</code> 的契约计算并返回结果。
     *
     * @param uri 请求目标 URI
     * @return 当前操作得到的 <code>Builder</code> 结果
     */
    public static Builder newBuilder(URI uri) {
        return new Builder(uri);
    }

    /** 集中保存 <code>uri</code>、<code>headers</code>、<code>contentType</code>、<code>method</code>，供当前模块使用。 */
    public static final class Builder {
        /** 发起请求所使用的目标 URI。 */
        private final URI uri;
        /** HTTP 消息的头部字段。 */
        private final List<Header> headers = new ArrayList<>();
        /** 保存 <code>"text/plain; charset=UTF-8"</code>，供 <code>Builder</code> 的处理流程复用。 */
        private String contentType = "text/plain; charset=UTF-8";
        /** 保存 <code>"GET"</code>，供 <code>Builder</code> 的处理流程复用。 */
        private String method = "GET";
        /** 保存 <code>body</code> 对应的 <code>String</code> 数据。 */
        private String body;
        /** 等待 HTTP 操作完成的时间上限。 */
        private Duration timeout;

        /**
         * 初始化 <code>Builder</code> 使用的 <code>uri</code>。
         *
         * @param uri 请求目标 URI
         */
        private Builder(URI uri) {
            this.uri = uri;
        }

        /**
         * 读取<code>this</code> 的当前值。
         *
         * @param name 传入此操作的 <code>String</code> 类型 <code>name</code>
         * @param value 由当前成员保存或用于计算的值
         * @return 返回表达式 <code>this</code>
         */
        public Builder header(String name, String value) {
            headers.add(new BasicHeader(name, value));
            if ("Content-Type".equalsIgnoreCase(name)) {
                contentType = value;
            }
            return this;
        }

        /**
         * 读取<code>this</code> 的当前值。
         *
         * @param timeout 等待 HTTP 操作完成的时间上限
         * @return 返回表达式 <code>this</code>
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * 读取<code>this</code> 的当前值。
         *
         * @param method 传入此操作的 <code>String</code> 类型 <code>method</code>
         * @param body 此 HTTP 消息携带的正文
         * @return 返回表达式 <code>this</code>
         */
        public Builder method(String method, String body) {
            this.method = method;
            this.body = body;
            return this;
        }

        /**
         * 按 <code>GET</code> 的契约计算并返回结果。
         *
         * @return 返回表达式 <code>method("GET", null)</code>
         */
        public Builder GET() {
            return method("GET", null);
        }

        /**
         * 按 <code>DELETE</code> 的契约计算并返回结果。
         *
         * @return 返回表达式 <code>method("DELETE", null)</code>
         */
        public Builder DELETE() {
            return method("DELETE", null);
        }

        /**
         * 按 <code>POST</code> 的契约计算并返回结果。
         *
         * @param body 此 HTTP 消息携带的正文
         * @return 返回表达式 <code>method("POST", body)</code>
         */
        public Builder POST(String body) {
            return method("POST", body);
        }

        /**
         * 按 <code>PUT</code> 的契约计算并返回结果。
         *
         * @param body 此 HTTP 消息携带的正文
         * @return 返回表达式 <code>method("PUT", body)</code>
         */
        public Builder PUT(String body) {
            return method("PUT", body);
        }

        /**
         * 执行 <code>build</code>，调用 <code>headers.forEach</code>、<code>ContentType.parse</code>、<code>request.setEntity</code>。
         *
         * @return 当前操作得到的 <code>HttpRequest</code> 结果
         */
        public HttpRequest build() {
            HttpUriRequestBase request = new HttpUriRequestBase(method, uri);
            headers.forEach(request::addHeader);
            if (Objects.nonNull(body)) {
                ContentType mediaType = ContentType.parse(contentType);
                request.setEntity(new StringEntity(body, mediaType));
            }
            if (Objects.nonNull(timeout)) {
                request.setConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofMilliseconds(timeout.toMillis())).build());
            }
            return new HttpRequest(request);
        }
    }

    /** 集中保存 <code>BodyPublishers</code> 的声明，供当前模块使用。 */
    public static final class BodyPublishers {
        /** 初始化 <code>BodyPublishers</code> 并委托其默认构造路径。 */
        private BodyPublishers() {
        }

        /**
         * 读取HTTP 请求或响应携带的正文。
         *
         * @param body 此 HTTP 消息携带的正文
         * @return 返回表达式 <code>body</code>
         */
        public static String ofString(String body) {
            return body;
        }

        /**
         * 读取<code>null</code> 的当前值。
         *
         * @return 返回表达式 <code>null</code>
         */
        public static String noBody() {
            return null;
        }
    }
}
