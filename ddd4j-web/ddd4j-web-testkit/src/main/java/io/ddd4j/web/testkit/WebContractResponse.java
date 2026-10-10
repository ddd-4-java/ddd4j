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

    private static final long serialVersionUID = 0L;

    private final int status;

    private final Map<String, List<String>> headers;

    private final String body;

    public Optional<String> firstHeader(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name)).map(Map.Entry::getValue).filter(values -> Objects.nonNull(values) && !values.isEmpty()).map(values -> values.get(0)).findFirst();
    }

    @JsonCreator()
    public WebContractResponse(@JsonProperty("status") int status, @JsonProperty("headers") Map<String, List<String>> headers, @JsonProperty("body") String body) {
        this.status = status;
        this.headers = headers;
        this.body = body;
    }

    @JsonProperty("status")
    public int status() {
        return status;
    }

    @JsonProperty("headers")
    public Map<String, List<String>> headers() {
        return headers;
    }

    @JsonProperty("body")
    public String body() {
        return body;
    }

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

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Integer.hashCode(status);
        result = 31 * result + Objects.hashCode(headers);
        result = 31 * result + Objects.hashCode(body);
        return result;
    }

    @Override
    public String toString() {
        return "WebContractResponse[status=" + status + ", headers=" + headers + ", body=" + body + "]";
    }
}
