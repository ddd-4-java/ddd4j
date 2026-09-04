package io.ddd4j.data.mybatis.adapter;

import java.util.Arrays;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class Ddd4jSqlObservationSinkTest {

    @AfterEach
    void cleanup() {
        ThreadContext.clear();
    }

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
