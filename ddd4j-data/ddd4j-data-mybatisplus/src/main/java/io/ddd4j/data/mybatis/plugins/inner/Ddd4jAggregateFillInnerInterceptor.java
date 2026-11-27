package io.ddd4j.data.mybatis.plugins.inner;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 在原生 MyBatis 查询完成后执行 ddd4j 聚合根填充。
 *
 * <p>This adapter intentionally remains in ddd4j because {@link Query},
 * {@link AggregateRoot} and {@link Page} are ddd4j concepts.</p>
 */
@SuppressWarnings({"rawtypes", "unchecked"})
@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class})
})
public class Ddd4jAggregateFillInnerInterceptor implements Interceptor {

    /**
     * 读取<code>result</code> 的当前值。
     *
     * @param invocation 传入此操作的 <code>Invocation</code> 类型 <code>invocation</code>
     * @return 返回表达式 <code>result</code>
     * @throws Throwable 完成 <code>intercept</code> 操作失败
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object result = invocation.proceed();
        if (result instanceof List<?> results) {
            afterQuery(invocation.getArgs()[1], results);
        }
        return result;
    }

    /**
     * 执行 <code>plugin</code>，调用 <code>Plugin.wrap</code>。
     *
     * @param target 传入此操作的 <code>Object</code> 类型 <code>target</code>
     * @return 返回表达式 <code>Plugin.wrap(target, this)</code>
     */
    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    /**
     * 执行 <code>after Query</code>，调用 <code>this.findQuery</code>、<code>results.isEmpty</code>、<code>aggregates.isEmpty</code>。
     *
     * @param parameter 传入此操作的 <code>Object</code> 类型 <code>parameter</code>
     * @param results 传入此操作的 <code>List&lt;?&gt;</code> 类型 <code>results</code>
     */
    public void afterQuery(Object parameter, List<?> results) {
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

    /**
     * 按 <code>find Query</code> 的契约计算并返回结果。
     *
     * @param parameter 传入此操作的 <code>Object</code> 类型 <code>parameter</code>
     * @return 按条件返回 <code>(Query&lt;?&gt;) parameter</code>、<code>(Query&lt;?&gt;) value</code>、<code>null</code>
     */
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

    /**
     * 执行 <code>collect Aggregates</code>，调用 <code>aggregates.add</code>。
     *
     * @param result 传入此操作的 <code>Object</code> 类型 <code>result</code>
     * @param aggregates 传入此操作的 <code>List&lt;AggregateRoot&lt;?&gt;&gt;</code> 类型 <code>aggregates</code>
     */
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
