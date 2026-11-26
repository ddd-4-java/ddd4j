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
    private final HttpUriRequestBase request;

    private HttpRequest(HttpUriRequestBase request) {
        this.request = request;
    }

    HttpUriRequestBase apacheRequest() {
        return request;
    }

    public static Builder newBuilder(URI uri) {
        return new Builder(uri);
    }

    public static final class Builder {
        private final URI uri;
        private final List<Header> headers = new ArrayList<>();
        private String contentType = "text/plain; charset=UTF-8";
        private String method = "GET";
        private String body;
        private Duration timeout;

        private Builder(URI uri) {
            this.uri = uri;
        }

        public Builder header(String name, String value) {
            headers.add(new BasicHeader(name, value));
            if ("Content-Type".equalsIgnoreCase(name)) {
                contentType = value;
            }
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder method(String method, String body) {
            this.method = method;
            this.body = body;
            return this;
        }

        public Builder GET() {
            return method("GET", null);
        }

        public Builder DELETE() {
            return method("DELETE", null);
        }

        public Builder POST(String body) {
            return method("POST", body);
        }

        public Builder PUT(String body) {
            return method("PUT", body);
        }

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

    public static final class BodyPublishers {
        private BodyPublishers() {
        }

        public static String ofString(String body) {
            return body;
        }

        public static String noBody() {
            return null;
        }
    }
}
