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
package io.ddd4j.data.mybatis.plugins.observation;

import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通过 <code>ServiceLoader.load()</code>、<code>sink.accept()</code>、<code>Class.forName()</code> 验证 <code>Ddd4jSqlObservationSink</code> 的运行行为。
 */
class Ddd4jSqlObservationSinkTest {

    /**
     * 测试后依次清除<code>ThreadContext</code>。
     */
    @AfterEach
    void cleanup() {
        ThreadContext.clear();
    }

    /**
     * 验证<code>ThreadContext.&lt;Object&gt;get(ContextConstants.PREPARING_SQL)</code> 等于 <code>"SELECT * FROM orders"</code>，<code>ThreadContext.&lt;Object&gt;get(ContextConstants.LAST_SQL_SPENDS)</code> 等于 <code>3L</code>。
     */
    @Test
    void shouldStoreNormalizedSqlAndElapsedTime() {
        SqlObservation observation = new SqlObservation(
                "OrderMapper.select", "SELECT  *\nFROM orders", 3_500_000L, null);

        new Ddd4jSqlObservationSink().accept(observation);

        assertThat(ThreadContext.<Object>get(ContextConstants.PREPARING_SQL))
                .isEqualTo("SELECT * FROM orders");
        assertThat(ThreadContext.<Object>get(ContextConstants.LAST_SQL_SPENDS))
                .isEqualTo(3L);
    }

    /**
     * 验证<code>discovered</code>为真。
     */
    @Test
    void shouldBeDiscoverableThroughServiceLoader() {
        boolean discovered = false;
        for (SqlObservationSink sink : ServiceLoader.load(SqlObservationSink.class)) {
            if (sink instanceof Ddd4jSqlObservationSink) {
                discovered = true;
                break;
            }
        }

        assertThat(discovered).isTrue();
    }

    /**
     * 验证<code>ThreadContext.&lt;Object&gt;get(ContextConstants.PREPARING_SQL)</code> 等于 <code>"SELECT * FROM orders"</code>，<code>ThreadContext.&lt;Object&gt;get(ContextConstants.LAST_SQL_SPENDS)</code> 等于 <code>3L</code>。
     */
    @Test
    void shouldForwardObservationDiscoveredThroughUpstreamServiceLoader() {
        com.baomidou.mybatisplus.enhance.observation.SqlObservationSink sink = ServiceLoader
                .load(com.baomidou.mybatisplus.enhance.observation.SqlObservationSink.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(Ddd4jSqlObservationSink.class::isInstance)
                .findFirst()
                .orElseThrow();

        sink.accept(new com.baomidou.mybatisplus.enhance.observation.SqlObservation(
                "OrderMapper.select", "SELECT  *\nFROM orders", 3_500_000L, null));

        assertThat(ThreadContext.<Object>get(ContextConstants.PREPARING_SQL))
                .isEqualTo("SELECT * FROM orders");
        assertThat(ThreadContext.<Object>get(ContextConstants.LAST_SQL_SPENDS)).isEqualTo(3L);
    }

    /**
     * 验证<code>sinkType.isInstance(new Ddd4jSqlObservationSink())</code>为真。
     *
     * @throws Exception 反射访问被测成员时失败
     */
    @Test
    void upstreamObservationMustReachDdd4jContext() throws Exception {
        Class<?> sinkType = Class.forName("com.baomidou.mybatisplus.enhance.observation.SqlObservationSink");
        assertThat(sinkType.isInstance(new Ddd4jSqlObservationSink())).isTrue();
    }
}
