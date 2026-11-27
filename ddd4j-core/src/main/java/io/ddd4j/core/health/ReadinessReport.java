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

    /**
     * 报告中的所有必要检查是否已通过。
     */
    private final boolean ready;
    /**
     * 每个就绪贡献者的检查结果。
     */
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
     * 执行组件就绪检查，并把检查异常或空结果转换成失败结果。
     *
     * @param contributor 参加就绪状态汇总的组件
     * @return 组件的检查结果或失败诊断
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

    /**
     * 根据<code>ready</code>比较对象的值是否相等。
     *
     * @param obj 用来比较或转换的输入对象
     * @return 对象满足此实现的相等条件时为真
     */
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

    /**
     * 计算与对象相等条件一致的哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Boolean.hashCode(ready);
        result = 31 * result + results.hashCode();
        return result;
    }

    /**
     * 生成用于调试和日志的对象描述，包含<code>ready</code>、<code>results</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "ReadinessReport[ready=" + ready + ", results=" + results + ']';
    }

}
