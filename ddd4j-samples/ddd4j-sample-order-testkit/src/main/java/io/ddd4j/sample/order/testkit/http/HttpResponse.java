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
    private final URI uri;
    private final int code;
    private final Headers headers;
    private final T body;
    private final String version;

    HttpResponse(URI uri, int code, Header[] headers, T body, String version) {
        this.uri = uri;
        this.code = code;
        this.headers = new Headers(headers);
        this.body = body;
        this.version = version;
    }

    public URI uri() { return uri; }

    public int statusCode() { return code; }

    public T body() { return body; }

    public String version() { return version; }

    public Headers headers() { return headers; }

    public static final class Headers {
        private final Map<String, List<String>> values = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        private Headers(Header[] headers) {
            for (Header header : headers) {
                values.computeIfAbsent(header.getName(), key -> new ArrayList<>()).add(header.getValue());
            }
        }

        public Optional<String> firstValue(String name) {
            List<String> header = values.get(name);
            return Objects.isNull(header) ? Optional.empty() : Optional.of(header.get(0));
        }

        public Map<String, List<String>> map() {
            return values;
        }
    }

    public interface BodyHandler<T> {
        T read(org.apache.hc.core5.http.HttpEntity entity) throws IOException;
    }

    public static final class BodyHandlers {
        private BodyHandlers() {
        }

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
