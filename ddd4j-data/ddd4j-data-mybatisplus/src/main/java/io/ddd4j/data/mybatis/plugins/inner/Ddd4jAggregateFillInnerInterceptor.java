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

import com.baomidou.mybatisplus.enhance.plugins.inner.EnhanceInnerInterceptor;
import com.baomidou.mybatisplus.enhance.util.ParameterUtils;
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
import java.util.Objects;
import java.util.Properties;

/**
 * 通过原生 MyBatis 插件或 MyBatis-Plus 增强链填充 Mapper 直接返回的领域聚合根。
 *
 * <p>如果 Mapper 返回持久化对象，由仓储在转换为聚合根之后填充。</p>
 */
@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class})
})
public class Ddd4jAggregateFillInnerInterceptor implements EnhanceInnerInterceptor, Interceptor {

    /**
     * 执行原生 MyBatis 查询后，填充结果中的聚合根。
     *
     * @param invocation MyBatis 查询调用
     * @return 原始查询结果
     * @throws Throwable 原始查询抛出的异常
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object result = invocation.proceed();
        if (result instanceof List<?>) {
            afterQuery(invocation.getArgs()[1], (List<?>) result);
        }
        return result;
    }

    /**
     * 将拦截器包装到 MyBatis 目标对象。
     *
     * @param target 目标对象
     * @return 包装后的对象
     */
    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    /**
     * 保留原生 MyBatis 拦截器的默认配置行为。
     *
     * @param properties 插件配置
     */
    @Override
    public void setProperties(Properties properties) {
        Interceptor.super.setProperties(properties);
    }

    @Override
    public List<Object> afterQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds,
                                   ResultHandler<?> resultHandler, BoundSql boundSql, List<Object> results) {
        afterQuery(parameter, results);
        return results;
    }

    /**
     * 从查询参数中查找领域查询，并填充查询结果中的聚合根。
     *
     * @param parameter MyBatis 查询参数，可直接为 Query 或包含 Query 的 Map
     * @param results 查询结果
     */
    public void afterQuery(Object parameter, List<?> results) {
        Query<?> query = this.findQuery(parameter);
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
        for (Object candidate : ParameterUtils.extractParameters(parameter)) {
            if (candidate instanceof Query<?>) {
                return (Query<?>) candidate;
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
