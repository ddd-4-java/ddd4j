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
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** \u901a\u8fc7 <code>ReadinessResult.ready()</code>\u3001<code>ReadinessResult.unavailable()</code>\u3001<code>Collections.singletonList()</code> \u9a8c\u8bc1 <code>RuntimeReadinessRegistry</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class RuntimeReadinessRegistryTest {

    /** \u9a8c\u8bc1<code>registry.readiness().ready()</code>\u4e3a\u771f\uff0c<code>registry.readiness().ready()</code>\u4e3a\u5047\uff0c<code>registry.unregister(cache)</code>\u4e3a\u771f\u3002 */
    @Test
    void shouldAggregateContributorsAndAllowRuntimeRegistrationChanges() {
        ReadinessContributor database = () -> ReadinessResult.ready("database");
        ReadinessContributor cache = () -> ReadinessResult.unavailable("cache", "unreachable");
        RuntimeReadinessRegistry registry = new RuntimeReadinessRegistry(Collections.singletonList(database));

        assertThat(registry.readiness().ready()).isTrue();

        registry.register(cache);
        assertThat(registry.readiness().ready()).isFalse();
        assertThat(registry.readiness().results()).extracting(ReadinessResult::name)
                .containsExactly("database", "cache");

        assertThat(registry.unregister(cache)).isTrue();
        assertThat(registry.readiness().ready()).isTrue();
    }

    /** \u9a8c\u8bc1<code>registry.registerAll(null)</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>registry</code>\uff0c<code>registry.registerAll(withNull)</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>registry</code>\uff0c<code>registry.contributors()</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\u3002 */
    @Test
    void shouldFilterNullCollectionAndElementsInRegisterAll() {
        RuntimeReadinessRegistry registry = new RuntimeReadinessRegistry();

        assertThat(registry.registerAll(null)).isSameAs(registry);
        List<ReadinessContributor> withNull = new ArrayList<>(Arrays.asList(
                () -> ReadinessResult.ready("a"), null));
        assertThat(registry.registerAll(withNull)).isSameAs(registry);

        assertThat(registry.contributors()).hasSize(1);
    }

    /** \u9a8c\u8bc1<code>registry.register(null)</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void shouldRejectNullContributorOnRegister() {
        RuntimeReadinessRegistry registry = new RuntimeReadinessRegistry();
        assertThatThrownBy(() -> registry.register(null))
                .isInstanceOf(NullPointerException.class);
    }

    /** \u9a8c\u8bc1<code>snapshot</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>UnsupportedOperationException.class</code>\u3002 */
    @Test
    void shouldReturnImmutableSnapshotOfContributors() {
        RuntimeReadinessRegistry registry = new RuntimeReadinessRegistry(
                Arrays.asList(() -> ReadinessResult.ready("a")));

        List<ReadinessContributor> snapshot = registry.contributors();
        registry.register(() -> ReadinessResult.ready("b"));

        assertThat(snapshot).hasSize(1);
        assertThatThrownBy(() -> snapshot.add(() -> ReadinessResult.ready("c")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** \u9a8c\u8bc1<code>registry.readiness().ready()</code>\u4e3a\u5047\uff0c<code>registry.readiness().results()</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\u3002 */
    @Test
    void shouldAggregateUnavailableContributorAsUnready() {
        RuntimeReadinessRegistry registry = new RuntimeReadinessRegistry(
                Arrays.asList(() -> ReadinessResult.unavailable("db", "not connected")));

        assertThat(registry.readiness().ready()).isFalse();
        assertThat(registry.readiness().results()).hasSize(1);
    }
}
