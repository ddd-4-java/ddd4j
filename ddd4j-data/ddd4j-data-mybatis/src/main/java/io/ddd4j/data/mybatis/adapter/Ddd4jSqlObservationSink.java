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

    /**
     * 接收调用方提交的记录。
     *
     * <p>此实现调用 <code>ThreadContext.set(ContextConstants.PREPARING_SQL, observation.sql())</code>。</p>
     *
     * @param observation SQL 观测数据
     */
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
