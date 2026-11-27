package io.ddd4j.data.mybatis.adapter;

import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 <code>Ddd4jSqlObservationSink</code> 在实际装配或测试替身中的行为。 */
class Ddd4jSqlObservationSinkTest {

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u6e05\u9664<code>ThreadContext</code>\u3002 */
    @AfterEach
    void cleanup() {
        ThreadContext.clear();
    }

    /** \u9a8c\u8bc1<code>ThreadContext.&lt;Object&gt;get(ContextConstants.PREPARING_SQL)</code> \u7b49\u4e8e <code>"SELECT * FROM orders"</code>\uff0c<code>ThreadContext.&lt;Object&gt;get(ContextConstants.SQL_PARAMS)</code> \u7b49\u4e8e <code>params</code>\uff0c<code>ThreadContext.&lt;Object&gt;get(ContextConstants.LAST_SQL_SPENDS)</code> \u7b49\u4e8e <code>8L</code>\u3002 */
    @Test
    void shouldStoreSqlObservationInThreadContext() {
        List<String> params = List.of("PAID", "2024");

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
}
