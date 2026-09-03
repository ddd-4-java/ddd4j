package io.ddd4j.core.health;

import io.ddd4j.kit.lang.StrKit;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * 单个就绪贡献者的检查结果。
 */
public final class ReadinessResult {

    private final String name;
    private final boolean ready;
    private final Map<String, String> details;

    public ReadinessResult(String name, boolean ready, Map<String, String> details) {
        if (StrKit.isBlank(name)) {
            throw new IllegalArgumentException("readiness contributor name must not be blank");
        }
        this.name = name;
        this.ready = ready;
        this.details = Collections.unmodifiableMap(
                details != null ? details : Collections.<String, String>emptyMap());
    }

    public static ReadinessResult ready(String name) {
        return new ReadinessResult(name, true, Collections.<String, String>emptyMap());
    }

    public static ReadinessResult unavailable(String name, String reason) {
        return new ReadinessResult(name, false, Collections.singletonMap("reason", reason));
    }

    public String getName() { return name; }
    public boolean isReady() { return ready; }
    public Map<String, String> getDetails() { return details; }

    public boolean ready() { return ready; }
    public String name() { return name; }
    public Map<String, String> details() { return details; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReadinessResult)) return false;
        ReadinessResult that = (ReadinessResult) o;
        return ready == that.ready
                && Objects.equals(name, that.name)
                && Objects.equals(details, that.details);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(name);
        result = 31 * result + (ready ? 1 : 0);
        result = 31 * result + Objects.hashCode(details);
        return result;
    }

    @Override
    public String toString() {
        return "ReadinessResult{name=" + name + ", ready=" + ready + ", details=" + details + '}';
    }
}
