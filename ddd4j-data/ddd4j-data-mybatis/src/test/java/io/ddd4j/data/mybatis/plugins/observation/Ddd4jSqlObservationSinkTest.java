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

import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQL 观测写入线程上下文的契约测试。
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
     * 验证<code>ThreadContext.&lt;Object&gt;get(ContextConstants.PREPARING_SQL)</code> 等于 <code>"SELECT * FROM orders"</code>，<code>ThreadContext.&lt;Object&gt;get(ContextConstants.SQL_PARAMS)</code> 等于 <code>params</code>，<code>ThreadContext.&lt;Object&gt;get(ContextConstants.LAST_SQL_SPENDS)</code> 等于 <code>8L</code>。
     */
    @Test
    void shouldStoreSqlObservationInThreadContext() {
        List<String> params = Arrays.asList("PAID", "2024");

        new Ddd4jSqlObservationSink().accept(
                new SqlObservation("io.ddd4j.data.mybatis.OrderMapper.listPaid", "SELECT * FROM orders", params, 8_000_000L, null));

        assertThat(ThreadContext.<Object>get(ContextConstants.PREPARING_SQL))
                .isEqualTo("SELECT * FROM orders");
        assertThat(ThreadContext.<Object>get(ContextConstants.SQL_PARAMS)).isEqualTo(params);
        assertThat(ThreadContext.<Object>get(ContextConstants.LAST_SQL_SPENDS)).isEqualTo(8L);
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
     * 验证<code>ThreadContext.&lt;Object&gt;get(ContextConstants.PREPARING_SQL)</code> 等于 <code>"SELECT * FROM orders WHERE state = ?"</code>，<code>ThreadContext.&lt;Object&gt;get(ContextConstants.SQL_PARAMS)</code> 等于 <code>params</code>，<code>ThreadContext.&lt;Object&gt;get(ContextConstants.LAST_SQL_SPENDS)</code> 等于 <code>8L</code>。
     */
    @Test
    void shouldForwardObservationDiscoveredThroughUpstreamServiceLoader() {
        org.apache.ibatis.enhance.spi.SqlObservationSink sink = ServiceLoader
                .load(org.apache.ibatis.enhance.spi.SqlObservationSink.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(Ddd4jSqlObservationSink.class::isInstance)
                .findFirst()
                .orElseThrow();

        List<String> params = Arrays.asList("PAID", "2024");
        sink.accept(new org.apache.ibatis.enhance.spi.SqlObservation(
                "OrderMapper.listPaid", "SELECT * FROM orders WHERE state = ?", params, 8_500_000L, null));

        assertThat(ThreadContext.<Object>get(ContextConstants.PREPARING_SQL))
                .isEqualTo("SELECT * FROM orders WHERE state = ?");
        assertThat(ThreadContext.<Object>get(ContextConstants.SQL_PARAMS)).isEqualTo(params);
        assertThat(ThreadContext.<Object>get(ContextConstants.LAST_SQL_SPENDS)).isEqualTo(8L);
    }
}
