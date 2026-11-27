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
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通过 <code>dataSource()</code>、<code>properties.setProperty()</code>、<code>interceptor.setProperties()</code> 验证 <code>Ddd4jMybatisAggregateFillBehavior</code> 的运行行为。
 */
class Ddd4jMybatisAggregateFillBehaviorTest {

    /**
     * 读取 <code>dataSource</code> 供当前测试使用。
     *
     * @return 当前测试使用的结果，取自 <code>dataSource</code>
     */
    private static JdbcDataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:");
        return dataSource;
    }

    /**
     * 核对<code>results</code> 的大小为 <code>1</code>，<code>results.get(0).id()</code> 等于 <code>"aggregate-1"</code>。
     *
     * @param factory 建立当前测试所需运行组件的工厂
     */
    private static void assertFilledExactlyOnce(SqlSessionFactory factory) {
        try (SqlSession session = factory.openSession()) {
            TrackingQuery query = new TrackingQuery();
            List<TestAggregate> results = session.getMapper(AggregateMapper.class).find(query);
            assertThat(results).hasSize(1);
            assertThat(results.get(0).id()).isEqualTo("aggregate-1");
            assertThat(query.fillCount).isEqualTo(1);
            assertThat(query.filled).hasSize(1);
            assertThat(query.filled.get(0)).isSameAs(results.get(0));
        }
    }

    /**
     * 在测试框架中执行 <code>dataSource()</code>，验证该场景。
     */
    @Test
    void fillsMapperAggregateExactlyOnceThroughGuiceAfterARealQuery() {
        SqlSessionFactory factory = new Ddd4jMybatisGuiceModule(dataSource())
                .addMapper(AggregateMapper.class)
                .sqlSessionFactory();

        assertFilledExactlyOnce(factory);
    }

    /**
     * 验证<code>properties.getProperty("unused")</code> 等于 <code>"sentinel"</code>，<code>properties.size()</code> 等于 <code>1</code>。
     */
    @Test
    void preservesDefaultPropertiesAndFillsThroughNativeMyBatisPlugin() {
        Interceptor interceptor = new QueryResultCallbackInterceptor(new AggregateQueryResultFiller()::fill);
        Properties properties = new Properties();
        properties.setProperty("unused", "sentinel");
        interceptor.setProperties(properties);
        assertThat(properties.getProperty("unused")).isEqualTo("sentinel");
        assertThat(properties.size()).isEqualTo(1);

        Configuration configuration = new Configuration(
                new Environment("aggregate-fill", new JdbcTransactionFactory(), dataSource()));
        configuration.addInterceptor(interceptor);
        configuration.addMapper(AggregateMapper.class);

        assertFilledExactlyOnce(new SqlSessionFactoryBuilder().build(configuration));
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
