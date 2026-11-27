package io.ddd4j.data.mybatis.adapter;

import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.data.mybatis.plugins.observation.Ddd4jSqlObservationSink;
import io.ddd4j.data.mybatis.plugins.observation.SqlObservation;
import io.ddd4j.data.mybatis.plugins.observation.SqlObservationSink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 <code>Ddd4jSqlObservationSink</code> 在实际装配或测试替身中的行为。 */
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
}
