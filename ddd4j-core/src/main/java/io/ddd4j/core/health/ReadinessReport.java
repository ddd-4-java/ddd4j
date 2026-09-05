/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.ddd4j.core.health;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 多个 {@link ReadinessContributor} 的聚合结果。
 *
 * <p>任一关键依赖未就绪或检查异常时，报告均为未就绪。检查异常只转换为安全的状态原因，
 * 原始异常应由 Runtime 的日志或观测系统记录。
 *
 * @param ready   是否可接收流量
 * @param results 每个已执行 Contributor 的结果
 */
public final class ReadinessReport {

    private final boolean ready;
    private final List<ReadinessResult> results;

    public ReadinessReport(boolean ready, List<ReadinessResult> results) {
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

        /**
     * 执行并汇总贡献者。
     *
     * @param contributors 依赖检查器集合
     * @return 聚合就绪报告
     */

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
            ReadinessResult result = contributor.check();
            if (result != null) {
                return result;
            }
            return ReadinessResult.unavailable(contributor.getClass().getSimpleName(), "empty result");
        } catch (RuntimeException exception) {
            return ReadinessResult.unavailable(contributor.getClass().getSimpleName(), "check failed");
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

    public boolean isReady() {
        return ready;
    }

    public List<ReadinessResult> getResults() {
        return results;
    }
}
