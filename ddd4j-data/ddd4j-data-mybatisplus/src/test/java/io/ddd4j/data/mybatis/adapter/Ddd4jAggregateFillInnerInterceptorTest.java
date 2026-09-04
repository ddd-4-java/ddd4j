package io.ddd4j.data.mybatis.adapter;

import java.util.Collections;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.data.mybatis.plugins.inner.Ddd4jAggregateFillInnerInterceptor;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Ddd4jAggregateFillInnerInterceptorTest {

    @Test
    void shouldBridgeQueryAndAggregateResults() throws Exception {
        TrackingQuery query = new TrackingQuery();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("query", query);
        TestAggregate aggregate = new TestAggregate("1");

        new Ddd4jAggregateFillInnerInterceptor().afterQuery(parameters, Collections.singletonList(aggregate));

        assertThat(query.filled).hasSize(1);
        assertThat(query.filled.get(0)).isSameAs(aggregate);
    }

    static final class TrackingQuery extends Query<TestAggregate> {

        private List<? extends AggregateRoot<?>> filled;

        @Override
        public void doFills(List<? extends AggregateRoot<?>> models) {
            this.filled = models;
        }
    }

    static final class TestAggregate extends AggregateRoot<String> {

        private final String id;

        TestAggregate(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }
}
