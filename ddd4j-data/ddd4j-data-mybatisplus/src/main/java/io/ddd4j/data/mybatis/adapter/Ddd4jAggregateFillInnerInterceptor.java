package io.ddd4j.data.mybatis.adapter;

import com.baomidou.mybatisplus.enhance.interceptor.inner.EnhanceInnerInterceptor;
import io.ddd4j.core.api.Page;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bridges the generic mybatis-plus-enhance after-query hook to ddd4j aggregate filling.
 *
 * <p>This adapter intentionally remains in ddd4j because {@link Query},
 * {@link AggregateRoot} and {@link Page} are ddd4j concepts.</p>
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class Ddd4jAggregateFillInnerInterceptor implements EnhanceInnerInterceptor {

    @Override
    public void afterQuery(Executor executor,
                           MappedStatement mappedStatement,
                           Object parameter,
                           RowBounds rowBounds,
                           ResultHandler<?> resultHandler,
                           BoundSql boundSql,
                           List<Object> results) throws SQLException {
        Query query = this.findQuery(parameter);
        if (Objects.isNull(query) || Objects.isNull(results) || results.isEmpty()) {
            return;
        }
        List<AggregateRoot<?>> aggregates = new ArrayList<>();
        for (Object result : results) {
            collectAggregates(result, aggregates);
        }
        if (!aggregates.isEmpty()) {
            query.doFills(aggregates);
        }
    }

    private Query<?> findQuery(Object parameter) {
        if (parameter instanceof Query<?>) {
            return (Query<?>) parameter;
        }
        if (parameter instanceof Map<?, ?>) {
            for (Object value : ((Map<?, ?>) parameter).values()) {
                if (value instanceof Query<?>) {
                    return (Query<?>) value;
                }
            }
        }
        return null;
    }

    private void collectAggregates(Object result, List<AggregateRoot<?>> aggregates) {
        if (result instanceof AggregateRoot<?>) {
            aggregates.add((AggregateRoot<?>) result);
            return;
        }
        if (result instanceof Page<?>) {
            List<?> records = ((Page<?>) result).getRecords();
            if (Objects.nonNull(records)) {
                for (Object record : records) {
                    collectAggregates(record, aggregates);
                }
            }
        }
    }
}
