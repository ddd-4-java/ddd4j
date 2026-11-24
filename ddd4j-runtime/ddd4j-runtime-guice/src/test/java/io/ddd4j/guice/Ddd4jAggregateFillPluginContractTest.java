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
package io.ddd4j.guice;

import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import com.baomidou.mybatisplus.enhance.plugins.inner.QueryResultCallbackInterceptor;
import io.ddd4j.data.mybatis.repository.AggregateQueryResultFiller;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.SimpleExecutor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class Ddd4jAggregateFillPluginContractTest {

    @Test
    void registersCallbackInPlainMyBatisAndFillsOnce() {
        Configuration configuration = configuration();
        assertDoesNotThrow(() ->
                configuration.addInterceptor(new QueryResultCallbackInterceptor(new AggregateQueryResultFiller()::fill)));
        assertEquals(1, configuration.getInterceptors().size());

        try (SqlSession session = new SqlSessionFactoryBuilder().build(configuration).openSession()) {
            TrackingQuery query = new TrackingQuery();
            List<TestAggregate> results = session.getMapper(AggregateMapper.class).find(query);
            assertFilledOnce(results, query);
        }
    }

    @Test
    void interceptsSixArgumentQuery() throws Exception {
        Configuration configuration = configuration();
        MappedStatement statement = statement(configuration);
        TrackingQuery query = new TrackingQuery();
        BoundSql boundSql = statement.getBoundSql(query);
        QueryResultCallbackInterceptor interceptor = new QueryResultCallbackInterceptor(new AggregateQueryResultFiller()::fill);

        Executor executor = new SimpleExecutor(configuration, transaction(configuration));
        try {
            Executor wrapped = (Executor) assertDoesNotThrow(() -> interceptor.plugin(executor));
            CacheKey cacheKey = executor.createCacheKey(statement, query, RowBounds.DEFAULT, boundSql);
            List<TestAggregate> results = wrapped.query(
                    statement, query, RowBounds.DEFAULT, Executor.NO_RESULT_HANDLER, cacheKey, boundSql);
            assertFilledOnce(results, query);
        } finally {
            executor.close(false);
        }
    }

    @Test
    void interceptsInvocationAndFillsRealQueryResult() throws Exception {
        Configuration configuration = configuration();
        MappedStatement statement = statement(configuration);
        TrackingQuery query = new TrackingQuery();
        QueryResultCallbackInterceptor interceptor = new QueryResultCallbackInterceptor(new AggregateQueryResultFiller()::fill);
        Method method = Executor.class.getMethod("query", MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class);

        Executor executor = new SimpleExecutor(configuration, transaction(configuration));
        try {
            Invocation invocation = new Invocation(executor, method,
                    new Object[]{statement, query, RowBounds.DEFAULT, Executor.NO_RESULT_HANDLER});
            Object result = assertDoesNotThrow(() -> interceptor.intercept(invocation));
            assertFilledOnce((List<?>) result, query);
        } finally {
            executor.close(false);
        }
    }

    private Configuration configuration() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:");
        Configuration configuration = new Configuration(
                new Environment("legacy-plugin", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(AggregateMapper.class);
        return configuration;
    }

    private MappedStatement statement(Configuration configuration) {
        return configuration.getMappedStatement(AggregateMapper.class.getName() + ".find");
    }

    private Transaction transaction(Configuration configuration) {
        Environment environment = configuration.getEnvironment();
        return environment.getTransactionFactory().newTransaction(environment.getDataSource(), null, true);
    }

    private void assertFilledOnce(List<?> results, TrackingQuery query) {
        assertEquals(1, results.size());
        assertEquals("aggregate-1", ((TestAggregate) results.get(0)).id());
        assertEquals(1, query.fillCount);
        assertEquals(1, query.filled.size());
        assertSame(results.get(0), query.filled.get(0));
    }

    public interface AggregateMapper {

        @Select("SELECT 'aggregate-1' AS id")
        List<TestAggregate> find(@Param("query") TrackingQuery query);
    }

    public static final class TestAggregate extends AggregateRoot<String> {

        private String id;

        @Override
        public String id() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    public static final class TrackingQuery extends Query<TestAggregate> {

        private int fillCount;
        private List<? extends AggregateRoot<?>> filled;

        @Override
        public void doFills(List<? extends AggregateRoot<?>> models) {
            fillCount++;
            filled = models;
        }
    }
}
