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
package io.ddd4j.data.mybatis.adapter;

import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.data.mybatis.plugins.inner.Ddd4jAggregateFillInnerInterceptor;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
class Ddd4jAggregateFillInnerInterceptorTest {

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

    /** 记录填充结果的测试查询对象，用于断言拦截器回调。 */
    static final class TrackingQuery extends Query<TestAggregate> {

        private List<? extends AggregateRoot<?>> filled;

        /**
         * 执行处理逻辑。
         * @param models models 参数。
         */
        @Override
        public void doFills(List<? extends AggregateRoot<?>> models) {
            this.filled = models;
        }
    }

    /** 测试用聚合根，标识固定为构造传入值。 */
    static final class TestAggregate extends AggregateRoot<String> {

        private final String id;

        TestAggregate(String id) {
            this.id = id;
        }

        /**
         * id() 方法。
         * @return 结果字符串。
         */
        @Override
        public String id() {
            return id;
        }
    }
}
