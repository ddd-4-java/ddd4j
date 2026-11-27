package io.ddd4j.data.mybatis.adapter;

import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.data.mybatis.plugins.inner.Ddd4jAggregateFillInnerInterceptor;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 <code>Ddd4jAggregateFillInnerInterceptor</code> 在实际装配或测试替身中的行为。 */
class Ddd4jAggregateFillInnerInterceptorTest {

    /**
     * 验证<code>query.filled</code> 的大小为 <code>1</code>，<code>query.filled.get(0)</code> 指向同一实例 <code>aggregate</code>。
     *
     * @throws Exception 完成 <code>should Bridge Query And Aggregate Results</code> 操作失败
     */
    @Test
    void shouldBridgeQueryAndAggregateResults() throws Exception {
        TrackingQuery query = new TrackingQuery();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("query", query);
        TestAggregate aggregate = new TestAggregate("1");

        new Ddd4jAggregateFillInnerInterceptor().afterQuery(parameters, List.of(aggregate));

        assertThat(query.filled).hasSize(1);
        assertThat(query.filled.get(0)).isSameAs(aggregate);
    }

    /** 为测试场景提供 <code>Query</code> 的实现。 */
    static final class TrackingQuery extends Query<TestAggregate> {

        /** 保存 <code>filled</code> 对应的 <code>List&lt;? extends AggregateRoot&lt;?&gt;&gt;</code> 数据。 */
        private List<? extends AggregateRoot<?>> filled;

        /**
         * 执行 <code>do Fills</code> 对应的处理。
         *
         * @param models 传入此操作的 <code>List&lt;? extends AggregateRoot&lt;?&gt;&gt;</code> 类型 <code>models</code>
         */
        @Override
        public void doFills(List<? extends AggregateRoot<?>> models) {
            this.filled = models;
        }
    }

    /** 为测试场景提供 <code>AggregateRoot</code> 的实现。 */
    static final class TestAggregate extends AggregateRoot<String> {

        /** 保存 <code>id</code> 对应的 <code>String</code> 数据。 */
        private final String id;

        /**
         * 初始化 <code>TestAggregate</code> 使用的 <code>id</code>。
         *
         * @param id 传入此操作的 <code>String</code> 类型 <code>id</code>
         */
        TestAggregate(String id) {
            this.id = id;
        }

        /**
         * 读取<code>id</code> 的当前值。
         *
         * @return 返回表达式 <code>id</code>
         */
        @Override
        public String id() {
            return id;
        }
    }
}
