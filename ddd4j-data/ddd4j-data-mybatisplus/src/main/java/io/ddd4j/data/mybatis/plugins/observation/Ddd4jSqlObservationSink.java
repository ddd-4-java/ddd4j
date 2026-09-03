package io.ddd4j.data.mybatis.plugins.observation;

import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;

import java.util.Objects;

/**
 * 将 MyBatis-Plus SQL 观测结果写入 ddd4j 请求上下文。
 */
public class Ddd4jSqlObservationSink implements SqlObservationSink {

    @Override
    public void accept(SqlObservation observation) {
        if (Objects.isNull(observation)) {
            return;
        }
        String sql = observation.sql();
        if (Objects.nonNull(sql)) {
            ThreadContext.set(ContextConstants.PREPARING_SQL, sql.replaceAll("\\s+", " ").trim());
        }
        ThreadContext.set(ContextConstants.LAST_SQL_SPENDS, observation.elapsedMillis());
    }
}
