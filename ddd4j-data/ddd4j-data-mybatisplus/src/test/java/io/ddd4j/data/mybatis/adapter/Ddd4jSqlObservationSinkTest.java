package io.ddd4j.data.mybatis.adapter;

import com.baomidou.mybatisplus.enhance.observation.SqlObservation;
import com.baomidou.mybatisplus.enhance.observation.SqlObservationSink;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.data.mybatis.plugins.observation.Ddd4jSqlObservationSink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class Ddd4jSqlObservationSinkTest {

    @AfterEach
    void cleanup() {
        ThreadContext.clear();
    }

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
