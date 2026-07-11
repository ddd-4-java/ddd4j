package io.ddd4j.data.mybatis.adapter;

import com.baomidou.mybatisplus.enhance.observation.SqlObservation;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.data.mybatis.plugins.observation.Ddd4jSqlObservationListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Ddd4jSqlObservationListenerTest {

    @AfterEach
    void cleanup() {
        ThreadContext.clear();
    }

    @Test
    void shouldStoreNormalizedSqlAndElapsedTime() {
        SqlObservation observation = new SqlObservation(
                "OrderMapper.select", "SELECT  *\nFROM orders", 3_500_000L, null);

        new Ddd4jSqlObservationListener().onCompleted(observation);

        assertThat(ThreadContext.<Object>get(ContextConstants.PREPARING_SQL))
                .isEqualTo("SELECT * FROM orders");
        assertThat(ThreadContext.<Object>get(ContextConstants.LAST_SQL_SPENDS))
                .isEqualTo(3L);
    }
}
