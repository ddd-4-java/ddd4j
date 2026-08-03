package io.ddd4j.data.mybatis.adapter;

import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;

import java.util.Objects;

/**
 * 将原生 MyBatis SQL 观测结果写入 ddd4j 请求上下文。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class Ddd4jSqlObservationSink implements SqlObservationSink {

    @Override
    public void accept(SqlObservation observation) {
        if (Objects.isNull(observation)) {
            return;
        }
        ThreadContext.set(ContextConstants.PREPARING_SQL, observation.sql());
        ThreadContext.set(ContextConstants.SQL_PARAMS, observation.sortedParams());
        ThreadContext.set(ContextConstants.LAST_SQL_SPENDS, observation.elapsedMillis());
    }
}
