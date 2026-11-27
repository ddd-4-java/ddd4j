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

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 就绪报告的快照、诊断信息与失败语义契约测试。
 */
class ReadinessReportTest {

    /** \u9a8c\u8bc1<code>report.ready()</code>\u4e3a\u771f\uff0c<code>report.results()</code> \u7684\u5927\u5c0f\u4e3a <code>2</code>\u3002 */
    @Test
    void check_shouldReportReadyWhenAllContributorsAreReady() {
        ReadinessReport report = ReadinessReport.check(Arrays.asList(
                () -> ReadinessResult.ready("postgresql"),
                () -> ReadinessResult.ready("redis")));

        assertThat(report.ready()).isTrue();
        assertThat(report.results()).hasSize(2);
    }

    /** \u9a8c\u8bc1<code>report.ready()</code>\u4e3a\u5047\uff0c<code>report.results()</code> \u7684\u5927\u5c0f\u4e3a <code>2</code>\uff0c<code>result.details()</code> \u542b\u5bf9\u5e94\u6761\u76ee <code>"reason"</code>\u3001<code>"check failed"</code>\u3002 */
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


    /** \u9a8c\u8bc1<code>report.ready()</code>\u4e3a\u5047\uff0c<code>report.results()</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>ReadinessResult.unavailable("ReadinessContributor", "check failed")</code>\u3001<code>ReadinessResult.unavailable("ReadinessContributor", "empty result")</code>\u3001<code>ReadinessResult.ready("cache")</code>\u3002 */
    @Test
    void check_shouldReportAnonymousContributorFailuresWithoutInterruptingOtherChecks() {
        ReadinessContributor rejected = new ReadinessContributor() {
            @Override
            public ReadinessResult check() {
                throw new IllegalStateException("internal diagnostic");
            }
        };
        ReadinessContributor absent = new ReadinessContributor() {
            @Override
            public ReadinessResult check() {
                return null;
            }
        };

        ReadinessReport report = ReadinessReport.check(Arrays.asList(
                rejected, absent, () -> ReadinessResult.ready("cache")));

        assertThat(report.ready()).isFalse();
        assertThat(report.results()).containsExactly(
                ReadinessResult.unavailable("ReadinessContributor", "check failed"),
                ReadinessResult.unavailable("ReadinessContributor", "empty result"),
                ReadinessResult.ready("cache"));
    }

    /** \u9a8c\u8bc1<code>result.details()</code> \u542b\u5bf9\u5e94\u6761\u76ee <code>"reason"</code>\u3001<code>"initial"</code>\uff0c<code>result.details().put("extra", "value")</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\uff0c<code>report.results().clear()</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>report.ready()</code>\u4e3a\u5047\uff0c<code>report.results()</code> \u7684\u5927\u5c0f\u4e3a <code>2</code>\uff0c<code>report.results().get(1).details()</code> \u542b\u5bf9\u5e94\u6761\u76ee <code>"reason"</code>\u3001<code>"empty result"</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>result.toString()</code> \u7b49\u4e8e <code>"ReadinessResult[name=database, ready=false, details={Aa=first, BB=second, AaAa=third, BBBB=fourth}]"</code>\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
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
