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
import io.ddd4j.data.mybatis.plugins.inner.Ddd4jAggregateFillInnerInterceptor;
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

class Ddd4jMybatisAggregateFillBehaviorTest {

    @Test
    void fillsMapperAggregateExactlyOnceThroughGuiceAfterARealQuery() {
        SqlSessionFactory factory = new Ddd4jMybatisGuiceModule(dataSource())
                .addMapper(AggregateMapper.class)
                .sqlSessionFactory();

        assertFilledExactlyOnce(factory);
    }

    @Test
    void preservesDefaultPropertiesAndFillsThroughNativeMyBatisPlugin() {
        Interceptor interceptor = new Ddd4jAggregateFillInnerInterceptor();
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

    private static JdbcDataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:");
        return dataSource;
    }

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
