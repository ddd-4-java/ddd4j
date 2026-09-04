package io.ddd4j.web.testkit;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Web 契约测试使用的最小响应快照。
 */
public record WebContractResponse(int status, Map<String, List<String>> headers, String body) {

    public Optional<String> firstHeader(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .filter(values -> Objects.nonNull(values) && !values.isEmpty())
                .map(values -> values.get(0))
                .findFirst();
    }
}
