package io.ddd4j.core.health;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 就绪度汇总报告。
 */
public final class ReadinessReport {

    private final boolean ready;
    private final List<ReadinessResult> results;

    public ReadinessReport(boolean ready, List<ReadinessResult> results) {
        Objects.requireNonNull(results, "results must not be null");
        this.ready = ready;
        this.results = Collections.unmodifiableList(new ArrayList<>(
                results != null ? results : Collections.<ReadinessResult>emptyList()));
    }

    public boolean ready() {
        return ready;
    }

    public List<ReadinessResult> results() {
        return results;
    }

    public static ReadinessReport check(Collection<? extends ReadinessContributor> contributors) {
        List<ReadinessResult> results = new ArrayList<>();
        Collection<? extends ReadinessContributor> safe = contributors != null
                ? contributors : Collections.<ReadinessContributor>emptyList();
        for (ReadinessContributor contributor : safe) {
            if (contributor != null) {
                results.add(checkContributor(contributor));
            }
        }
        boolean allReady = true;
        for (ReadinessResult result : results) {
            if (!result.ready()) {
                allReady = false;
                break;
            }
        }
        return new ReadinessReport(allReady, results);
    }

    private static ReadinessResult checkContributor(ReadinessContributor contributor) {
        try {
            return contributor.check();
        } catch (Exception e) {
            return ReadinessResult.unavailable("unknown", e.getMessage());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReadinessReport)) return false;
        ReadinessReport that = (ReadinessReport) o;
        return ready == that.ready && results.equals(that.results);
    }

    @Override
    public int hashCode() {
        int result = (ready ? 1 : 0);
        result = 31 * result + results.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "ReadinessReport{ready=" + ready + ", results=" + results + '}';
    }
}
