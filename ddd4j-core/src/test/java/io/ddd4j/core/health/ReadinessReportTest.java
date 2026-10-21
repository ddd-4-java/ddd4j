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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 就绪报告的快照、诊断信息与失败语义契约测试。
 */
class ReadinessReportTest {

    @Test
    void check_shouldReportReadyWhenAllContributorsAreReady() {
        ReadinessReport report = ReadinessReport.check(Arrays.asList(
                () -> ReadinessResult.ready("postgresql"),
                () -> ReadinessResult.ready("redis")));

        assertThat(report.ready()).isTrue();
        assertThat(report.results()).hasSize(2);
    }

    @Test
    void check_shouldFailClosedWhenContributorThrows() {
        ReadinessReport report = ReadinessReport.check(Arrays.asList(
                () -> ReadinessResult.ready("postgresql"),
                () -> {
                    throw new IllegalStateException("redis connection refused");
                }));

        assertThat(report.ready()).isFalse();
        assertThat(report.results()).hasSize(2);
        assertThat(report.results()).extracting(ReadinessResult::name).contains("postgresql");
        assertThat(report.results()).anySatisfy(result -> {
            assertThat(result.ready()).isFalse();
            assertThat(result.details()).containsEntry("reason", "check failed");
        });
    }

    @Test
    void resultAndReport_shouldKeepImmutableSnapshots() {
        Map<String, String> details = new HashMap<>();
        details.put("reason", "initial");
        ReadinessResult result = new ReadinessResult("database", false, details);
        details.put("reason", "changed");
        assertThat(result.details()).containsEntry("reason", "initial");
        assertThatThrownBy(() -> result.details().put("extra", "value"))
                .isInstanceOf(UnsupportedOperationException.class);

        List<ReadinessResult> results = new ArrayList<>();
        results.add(result);
        ReadinessReport report = new ReadinessReport(false, results);
        results.clear();
        assertThat(report.results()).containsExactly(result);
        assertThatThrownBy(() -> report.results().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void check_shouldIgnoreNullContributorAndFailClosedOnEmptyResult() {
        ReadinessReport report = ReadinessReport.check(Arrays.asList(
                null,
                () -> ReadinessResult.ready("database"),
                () -> null));

        assertThat(report.ready()).isFalse();
        assertThat(report.results()).hasSize(2);
        assertThat(report.results().get(0).name()).isEqualTo("database");
        assertThat(report.results().get(1).ready()).isFalse();
        assertThat(report.results().get(1).details()).containsEntry("reason", "empty result");
    }

    @Test
    void result_shouldKeepDiagnosticTextInInputOrder() {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Aa", "first");
        details.put("BB", "second");
        details.put("AaAa", "third");
        details.put("BBBB", "fourth");

        ReadinessResult result = new ReadinessResult("database", false, details);

        assertThat(new ArrayList<>(result.details().keySet())).containsExactly("Aa", "BB", "AaAa", "BBBB");
        assertThat(result.toString()).isEqualTo(
                "ReadinessResult[name=database, ready=false, details={Aa=first, BB=second, AaAa=third, BBBB=fourth}]");
    }

    @Test
    void result_shouldRejectNullDiagnosticEntries() {
        Map<String, String> nullKey = new HashMap<>();
        nullKey.put(null, "value");
        assertThatThrownBy(() -> new ReadinessResult("database", false, nullKey))
                .isInstanceOf(NullPointerException.class);

        Map<String, String> nullValue = new HashMap<>();
        nullValue.put("reason", null);
        assertThatThrownBy(() -> new ReadinessResult("database", false, nullValue))
                .isInstanceOf(NullPointerException.class);
        assertThat(new ReadinessResult("database", true, null).details()).isEmpty();
    }

}
