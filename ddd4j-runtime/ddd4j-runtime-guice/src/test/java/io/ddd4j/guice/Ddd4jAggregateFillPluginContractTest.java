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

import com.baomidou.mybatisplus.enhance.plugins.inner.QueryResultCallbackInterceptor;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
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
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通过 <code>configuration()</code>、<code>configuration.addInterceptor()</code>、<code>configuration.getInterceptors()</code> 验证 <code>Ddd4jAggregateFillPluginContract</code> 的运行行为。
 */
class Ddd4jAggregateFillPluginContractTest {

    /**
     * 验证<code>configuration.getInterceptors().size()</code> 等于 <code>1</code>。
     */
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
     * 验证<code>interceptor.plugin(executor)</code> 正常返回。
     *
     * @throws Exception 准备或释放被测运行资源时失败
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
     * 验证<code>interceptor.intercept(invocation)</code> 正常返回。
     *
     * @throws Exception 准备或释放被测运行资源时失败
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
     * 读取 <code>configuration</code> 供当前测试使用。
     *
     * @return 当前测试使用的结果，取自 <code>configuration</code>
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
     * 通过 <code>configuration.getMappedStatement()</code> 取得当前测试需要的结果。
     *
     * @param configuration 传给 <code>configuration.getMappedStatement()</code> 的 <code>Configuration</code> 输入
     * @return 当前测试使用的结果，取自 <code>configuration.getMappedStatement(AggregateMapper.class.getName() + ".find")</code>
     */
    private MappedStatement statement(Configuration configuration) {
        return configuration.getMappedStatement(AggregateMapper.class.getName() + ".find");
    }

    /**
     * 通过 <code>environment.getTransactionFactory()</code> 取得当前测试需要的结果。
     *
     * @param configuration 传给 <code>configuration.getEnvironment()</code> 的 <code>Configuration</code> 输入
     * @return 当前测试使用的结果，取自 <code>environment.getTransactionFactory().newTransaction(environment.getDataSource(), null, true)</code>
     */
    private Transaction transaction(Configuration configuration) {
        Environment environment = configuration.getEnvironment();
        return environment.getTransactionFactory().newTransaction(environment.getDataSource(), null, true);
    }

    /**
     * 核对<code>results.size()</code> 等于 <code>1</code>，<code>((TestAggregate) results.get(0)).id()</code> 等于 <code>"aggregate-1"</code>。
     *
     * @param results 传给 <code>assertEquals()</code> 的 <code>List&lt;?&gt;</code> 输入
     * @param query   用来读取测试数据的查询对象
     */
    private void assertFilledOnce(List<?> results, TrackingQuery query) {
        assertEquals(1, results.size());
        assertEquals("aggregate-1", ((TestAggregate) results.get(0)).id());
        assertEquals(1, query.fillCount);
        assertEquals(1, query.filled.size());
        assertSame(results.get(0), query.filled.get(0));
    }

    /**
     * 提供 <code>find()</code> 等供当前测试使用的独立操作。
     */
    public interface AggregateMapper {

        /**
         * 按映射 SQL 读取测试记录。
         *
         * @param query 随映射查询传入的测试领域条件
         * @return 与查询匹配的映射结果
         */
        @Select("SELECT 'aggregate-1' AS id")
        List<TestAggregate> find(@Param("query") TrackingQuery query);
    }

    /**
     * 测试专用聚合，供验证聚合标识和仓储或事件处理行为。
     */
    public static final class TestAggregate extends AggregateRoot<String> {

        /**
         * 标识当前测试对象的主键或事件标识。
         */
        private String id;

        /**
         * 读取<code>id</code> 字段。
         *
         * @return <code>id</code> 字段
         */
        @Override
        public String id() {
            return id;
        }

        /**
         * 设置<code>id</code> 字段。
         *
         * @param id 要写入的值
         */
        public void setId(String id) {
            this.id = id;
        }
    }

    /**
     * 记录或暴露筛选条件的查询替身。
     */
    public static final class TrackingQuery extends Query<TestAggregate> {

        /**
         * 记录 ORM 结果填充逻辑实际执行的次数。
         */
        private int fillCount;
        /**
         * 保存已交给 ORM 结果填充入口的数据。
         */
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
