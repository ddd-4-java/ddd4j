package io.ddd4j.web.testkit;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Web 契约测试使用的最小响应快照。
 */
public final class WebContractResponse {
    private final int status;
    private final Map<String, List<String>> headers;
    private final String body;

    public WebContractResponse(int status, Map<String, List<String>> headers, String body) {
        this.status = status;
        this.headers = headers;
        this.body = body;
    }

    public int status() { return status; }
    public Map<String, List<String>> headers() { return headers; }
    public String body() { return body; }

    public Optional<String> firstHeader(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .filter(values -> Objects.nonNull(values) && !values.isEmpty())
                .map(values -> values.get(0))
                .findFirst();
    }

    @Override public boolean equals(Object o) {
        return this == o || (o instanceof WebContractResponse
                && status == ((WebContractResponse)o).status
                && Objects.equals(headers, ((WebContractResponse)o).headers)
                && Objects.equals(body, ((WebContractResponse)o).body));
    }
    @Override public int hashCode() { return Objects.hash(status, headers, body); }
    @Override public String toString() { return "WebContractResponse{status=" + status + "}"; }
}
