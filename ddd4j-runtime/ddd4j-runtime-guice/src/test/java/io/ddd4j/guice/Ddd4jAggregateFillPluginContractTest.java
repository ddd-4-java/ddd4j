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

/** \u901a\u8fc7 <code>configuration()</code>\u3001<code>configuration.addInterceptor()</code>\u3001<code>configuration.getInterceptors()</code> \u9a8c\u8bc1 <code>Ddd4jAggregateFillPluginContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class Ddd4jAggregateFillPluginContractTest {

    /** \u9a8c\u8bc1<code>configuration.getInterceptors().size()</code> \u7b49\u4e8e <code>1</code>\u3002 */
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

    /**
     * \u9a8c\u8bc1<code>interceptor.plugin(executor)</code> \u6b63\u5e38\u8fd4\u56de\u3002
     * @throws Exception \u51c6\u5907\u6216\u91ca\u653e\u88ab\u6d4b\u8fd0\u884c\u8d44\u6e90\u65f6\u5931\u8d25
     */
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

    /**
     * \u9a8c\u8bc1<code>interceptor.intercept(invocation)</code> \u6b63\u5e38\u8fd4\u56de\u3002
     * @throws Exception \u51c6\u5907\u6216\u91ca\u653e\u88ab\u6d4b\u8fd0\u884c\u8d44\u6e90\u65f6\u5931\u8d25
     */
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

    /**
     * \u8bfb\u53d6 <code>configuration</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>configuration</code>
     */
    private Configuration configuration() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:");
        Configuration configuration = new Configuration(
                new Environment("legacy-plugin", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(AggregateMapper.class);
        return configuration;
    }

    /**
     * \u901a\u8fc7 <code>configuration.getMappedStatement()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param configuration \u4f20\u7ed9 <code>configuration.getMappedStatement()</code> \u7684 <code>Configuration</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>configuration.getMappedStatement(AggregateMapper.class.getName() + ".find")</code>
     */
    private MappedStatement statement(Configuration configuration) {
        return configuration.getMappedStatement(AggregateMapper.class.getName() + ".find");
    }

    /**
     * \u901a\u8fc7 <code>environment.getTransactionFactory()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param configuration \u4f20\u7ed9 <code>configuration.getEnvironment()</code> \u7684 <code>Configuration</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>environment.getTransactionFactory().newTransaction(environment.getDataSource(), null, true)</code>
     */
    private Transaction transaction(Configuration configuration) {
        Environment environment = configuration.getEnvironment();
        return environment.getTransactionFactory().newTransaction(environment.getDataSource(), null, true);
    }

    /**
     * \u6838\u5bf9<code>results.size()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>((TestAggregate) results.get(0)).id()</code> \u7b49\u4e8e <code>"aggregate-1"</code>\u3002
     * @param results \u4f20\u7ed9 <code>assertEquals()</code> \u7684 <code>List&lt;?&gt;</code> \u8f93\u5165
     * @param query \u7528\u6765\u8bfb\u53d6\u6d4b\u8bd5\u6570\u636e\u7684\u67e5\u8be2\u5bf9\u8c61
     */
    private void assertFilledOnce(List<?> results, TrackingQuery query) {
        assertEquals(1, results.size());
        assertEquals("aggregate-1", ((TestAggregate) results.get(0)).id());
        assertEquals(1, query.fillCount);
        assertEquals(1, query.filled.size());
        assertSame(results.get(0), query.filled.get(0));
    }

    /** \u63d0\u4f9b <code>find()</code> \u7b49\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u72ec\u7acb\u64cd\u4f5c\u3002 */
    public interface AggregateMapper {

        /**
         * \u6309\u6620\u5c04 SQL \u8bfb\u53d6\u6d4b\u8bd5\u8bb0\u5f55\u3002
         * @param query \u968f\u6620\u5c04\u67e5\u8be2\u4f20\u5165\u7684\u6d4b\u8bd5\u9886\u57df\u6761\u4ef6
         * @return \u4e0e\u67e5\u8be2\u5339\u914d\u7684\u6620\u5c04\u7ed3\u679c
         */
        @Select("SELECT 'aggregate-1' AS id")
        List<TestAggregate> find(@Param("query") TrackingQuery query);
    }

    /** \u6d4b\u8bd5\u4e13\u7528\u805a\u5408\uff0c\u4f9b\u9a8c\u8bc1\u805a\u5408\u6807\u8bc6\u548c\u4ed3\u50a8\u6216\u4e8b\u4ef6\u5904\u7406\u884c\u4e3a\u3002 */
    public static final class TestAggregate extends AggregateRoot<String> {

        /** \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6\u3002 */
        private String id;

        /**
         * \u8bfb\u53d6<code>id</code> \u5b57\u6bb5\u3002
         * @return <code>id</code> \u5b57\u6bb5
         */
        @Override
        public String id() {
            return id;
        }

        /**
         * \u8bbe\u7f6e<code>id</code> \u5b57\u6bb5\u3002
         * @param id \u8981\u5199\u5165\u7684\u503c
         */
        public void setId(String id) {
            this.id = id;
        }
    }

    /** \u8bb0\u5f55\u6216\u66b4\u9732\u7b5b\u9009\u6761\u4ef6\u7684\u67e5\u8be2\u66ff\u8eab\u3002 */
    public static final class TrackingQuery extends Query<TestAggregate> {

        /** \u8bb0\u5f55 ORM \u7ed3\u679c\u586b\u5145\u903b\u8f91\u5b9e\u9645\u6267\u884c\u7684\u6b21\u6570\u3002 */
        private int fillCount;
        /** \u4fdd\u5b58\u5df2\u4ea4\u7ed9 ORM \u7ed3\u679c\u586b\u5145\u5165\u53e3\u7684\u6570\u636e\u3002 */
        private List<? extends AggregateRoot<?>> filled;

        /**
         * 补充当前查询执行所需的参数。
         *
         * <p>此实现更新<code>filled = models</code>。</p>
         *
         * @param models 待填充的聚合根列表
         */
        @Override
        public void doFills(List<? extends AggregateRoot<?>> models) {
            fillCount++;
            filled = models;
        }
    }
}
