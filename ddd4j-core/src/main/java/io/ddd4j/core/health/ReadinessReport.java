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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.kit.lang.StrKit;
import lombok.Getter;

import java.util.*;

/**
 * 多个 {@link ReadinessContributor} 的聚合结果。
 *
 * <p>任一关键依赖未就绪或检查异常时，报告均为未就绪。检查异常只转换为安全的状态原因，
 * 原始异常应由 Runtime 的日志或观测系统记录。
 *
 * <p>{@code ready} — 是否可接收流量
 * <p>{@code results} — 每个已执行 Contributor 的结果
 */
@Getter
public final class ReadinessReport {

    /** \u62a5\u544a\u4e2d\u7684\u6240\u6709\u5fc5\u8981\u68c0\u67e5\u662f\u5426\u5df2\u901a\u8fc7\u3002 */
    private final boolean ready;
    /** \u6bcf\u4e2a\u5c31\u7eea\u8d21\u732e\u8005\u7684\u68c0\u67e5\u7ed3\u679c\u3002 */
    private final List<ReadinessResult> results;

    /**
     * 多个 {@link ReadinessContributor} 的聚合结果。
     *
     * <p>任一关键依赖未就绪或检查异常时，报告均为未就绪。检查异常只转换为安全的状态原因，
     * 原始异常应由 Runtime 的日志或观测系统记录。
     *
     * @param ready   是否可接收流量
     * @param results 每个已执行 Contributor 的结果
     */
    @JsonCreator
    public ReadinessReport(@JsonProperty("ready") boolean ready,
                           @JsonProperty("results") List<ReadinessResult> results) {
        this.ready = ready;
        List<ReadinessResult> copiedResults = new ArrayList<>();
        if (Objects.nonNull(results)) {
            for (ReadinessResult result : results) {
                copiedResults.add(Objects.requireNonNull(result, "readiness result"));
            }
        }
        this.results = Collections.unmodifiableList(copiedResults);
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

    /**
     * \u6267\u884c\u7ec4\u4ef6\u5c31\u7eea\u68c0\u67e5\uff0c\u5e76\u628a\u68c0\u67e5\u5f02\u5e38\u6216\u7a7a\u7ed3\u679c\u8f6c\u6362\u6210\u5931\u8d25\u7ed3\u679c\u3002
     * @param contributor \u53c2\u52a0\u5c31\u7eea\u72b6\u6001\u6c47\u603b\u7684\u7ec4\u4ef6
     * @return \u7ec4\u4ef6\u7684\u68c0\u67e5\u7ed3\u679c\u6216\u5931\u8d25\u8bca\u65ad
     */
    private static ReadinessResult checkContributor(ReadinessContributor contributor) {
        String reason;
        try {
            ReadinessResult result = contributor.check();
            if (Objects.nonNull(result)) {
                return result;
            }
            reason = "empty result";
        } catch (RuntimeException exception) {
            reason = "check failed";
        }
        String name = contributor.getClass().getSimpleName();
        if (StrKit.isBlank(name)) {
            name = ReadinessContributor.class.getSimpleName();
        }
        return ReadinessResult.unavailable(name, reason);
    }

    /**
     * 获取整体就绪状态，供业务调用及 JSON 序列化使用。
     *
     * @return 整体就绪时为 true
     */
    @JsonProperty("ready")
    public boolean ready() {
        return ready;
    }

    /**
     * 获取各依赖的就绪结果，供业务调用及 JSON 序列化使用。
     *
     * @return 不可变的就绪结果列表
     */
    @JsonProperty("results")
    public List<ReadinessResult> results() {
        return results;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ReadinessReport)) {
            return false;
        }
        ReadinessReport that = (ReadinessReport) obj;
        return ready == that.ready && results.equals(that.results);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Boolean.hashCode(ready);
        result = 31 * result + results.hashCode();
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "ReadinessReport[ready=" + ready + ", results=" + results + ']';
    }

}
