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

/** \u901a\u8fc7 <code>dataSource()</code>\u3001<code>properties.setProperty()</code>\u3001<code>interceptor.setProperties()</code> \u9a8c\u8bc1 <code>Ddd4jMybatisAggregateFillBehavior</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class Ddd4jMybatisAggregateFillBehaviorTest {

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>dataSource()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void fillsMapperAggregateExactlyOnceThroughGuiceAfterARealQuery() {
        SqlSessionFactory factory = new Ddd4jMybatisGuiceModule(dataSource())
                .addMapper(AggregateMapper.class)
                .sqlSessionFactory();

        assertFilledExactlyOnce(factory);
    }

    /** \u9a8c\u8bc1<code>properties.getProperty("unused")</code> \u7b49\u4e8e <code>"sentinel"</code>\uff0c<code>properties.size()</code> \u7b49\u4e8e <code>1</code>\u3002 */
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
     * \u8bfb\u53d6 <code>dataSource</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>dataSource</code>
     */
    private static JdbcDataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:");
        return dataSource;
    }

    /**
     * \u6838\u5bf9<code>results</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\uff0c<code>results.get(0).id()</code> \u7b49\u4e8e <code>"aggregate-1"</code>\u3002
     * @param factory \u5efa\u7acb\u5f53\u524d\u6d4b\u8bd5\u6240\u9700\u8fd0\u884c\u7ec4\u4ef6\u7684\u5de5\u5382
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
