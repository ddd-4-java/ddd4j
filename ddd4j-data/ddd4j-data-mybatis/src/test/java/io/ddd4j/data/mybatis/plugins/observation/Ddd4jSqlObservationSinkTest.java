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
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/** SQL 观测写入线程上下文的契约测试。 */
class Ddd4jSqlObservationSinkTest {

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u6e05\u9664<code>ThreadContext</code>\u3002 */
    @AfterEach
    void cleanup() {
        ThreadContext.clear();
    }

    /** \u9a8c\u8bc1<code>ThreadContext.&lt;Object&gt;get(ContextConstants.PREPARING_SQL)</code> \u7b49\u4e8e <code>"SELECT * FROM orders"</code>\uff0c<code>ThreadContext.&lt;Object&gt;get(ContextConstants.SQL_PARAMS)</code> \u7b49\u4e8e <code>params</code>\uff0c<code>ThreadContext.&lt;Object&gt;get(ContextConstants.LAST_SQL_SPENDS)</code> \u7b49\u4e8e <code>8L</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>discovered</code>\u4e3a\u771f\u3002 */
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

    /** \u9a8c\u8bc1<code>ThreadContext.&lt;Object&gt;get(ContextConstants.PREPARING_SQL)</code> \u7b49\u4e8e <code>"SELECT * FROM orders WHERE state = ?"</code>\uff0c<code>ThreadContext.&lt;Object&gt;get(ContextConstants.SQL_PARAMS)</code> \u7b49\u4e8e <code>params</code>\uff0c<code>ThreadContext.&lt;Object&gt;get(ContextConstants.LAST_SQL_SPENDS)</code> \u7b49\u4e8e <code>8L</code>\u3002 */
    @Test
    void shouldForwardObservationDiscoveredThroughUpstreamServiceLoader() {
        org.apache.ibatis.enhance.spi.SqlObservationSink sink = StreamSupport.stream(ServiceLoader.load(org.apache.ibatis.enhance.spi.SqlObservationSink.class).spliterator(), false)
                .filter(Ddd4jSqlObservationSink.class::isInstance)
                .findFirst()
                .orElseThrow(java.util.NoSuchElementException::new);

        List<String> params = Arrays.asList("PAID", "2024");
        sink.accept(new org.apache.ibatis.enhance.spi.SqlObservation(
                "OrderMapper.listPaid", "SELECT * FROM orders WHERE state = ?", params, 8_500_000L, null));

        assertThat(ThreadContext.<Object>get(ContextConstants.PREPARING_SQL))
                .isEqualTo("SELECT * FROM orders WHERE state = ?");
        assertThat(ThreadContext.<Object>get(ContextConstants.SQL_PARAMS)).isEqualTo(params);
        assertThat(ThreadContext.<Object>get(ContextConstants.LAST_SQL_SPENDS)).isEqualTo(8L);
    }
}
