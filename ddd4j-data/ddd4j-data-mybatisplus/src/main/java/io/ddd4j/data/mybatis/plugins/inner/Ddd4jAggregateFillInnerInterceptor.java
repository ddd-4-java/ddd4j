/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object result = invocation.proceed();
        if (result instanceof List<?> results) {
            afterQuery(invocation.getArgs()[1], results);
        }
        return result;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

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
