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
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/** \u901a\u8fc7 <code>ServiceLoader.load()</code>\u3001<code>sink.accept()</code>\u3001<code>Class.forName()</code> \u9a8c\u8bc1 <code>Ddd4jSqlObservationSink</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class Ddd4jSqlObservationSinkTest {

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u6e05\u9664<code>ThreadContext</code>\u3002 */
    @AfterEach
    void cleanup() {
        ThreadContext.clear();
    }

    /** \u9a8c\u8bc1<code>ThreadContext.&lt;Object&gt;get(ContextConstants.PREPARING_SQL)</code> \u7b49\u4e8e <code>"SELECT * FROM orders"</code>\uff0c<code>ThreadContext.&lt;Object&gt;get(ContextConstants.LAST_SQL_SPENDS)</code> \u7b49\u4e8e <code>3L</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>ThreadContext.&lt;Object&gt;get(ContextConstants.PREPARING_SQL)</code> \u7b49\u4e8e <code>"SELECT * FROM orders"</code>\uff0c<code>ThreadContext.&lt;Object&gt;get(ContextConstants.LAST_SQL_SPENDS)</code> \u7b49\u4e8e <code>3L</code>\u3002 */
    @Test
    void shouldForwardObservationDiscoveredThroughUpstreamServiceLoader() {
        com.baomidou.mybatisplus.enhance.observation.SqlObservationSink sink = StreamSupport.stream(ServiceLoader.load(com.baomidou.mybatisplus.enhance.observation.SqlObservationSink.class).spliterator(), false)
                .filter(Ddd4jSqlObservationSink.class::isInstance)
                .findFirst()
                .orElseThrow(java.util.NoSuchElementException::new);

        sink.accept(new com.baomidou.mybatisplus.enhance.observation.SqlObservation(
                "OrderMapper.select", "SELECT  *\nFROM orders", 3_500_000L, null));

        assertThat(ThreadContext.<Object>get(ContextConstants.PREPARING_SQL))
                .isEqualTo("SELECT * FROM orders");
        assertThat(ThreadContext.<Object>get(ContextConstants.LAST_SQL_SPENDS)).isEqualTo(3L);
    }

    /**
     * \u9a8c\u8bc1<code>sinkType.isInstance(new Ddd4jSqlObservationSink())</code>\u4e3a\u771f\u3002
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
     */
    @Test
    void upstreamObservationMustReachDdd4jContext() throws Exception {
        Class<?> sinkType = Class.forName("com.baomidou.mybatisplus.enhance.observation.SqlObservationSink");
        assertThat(sinkType.isInstance(new Ddd4jSqlObservationSink())).isTrue();
    }
}
