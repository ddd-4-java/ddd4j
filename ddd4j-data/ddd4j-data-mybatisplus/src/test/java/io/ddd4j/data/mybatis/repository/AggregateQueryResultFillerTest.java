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
package io.ddd4j.data.mybatis.repository;

import com.baomidou.mybatisplus.enhance.util.ParameterUtils;

import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.api.Page;
import io.ddd4j.core.ddd.model.AggregateRoot;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** \u901a\u8fc7 <code>parameters.put()</code>\u3001<code>ParameterUtils.extractParameters()</code>\u3001<code>Collections.singletonList()</code> \u9a8c\u8bc1 <code>AggregateQueryResultFiller</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class AggregateQueryResultFillerTest {

    /**
     * \u9a8c\u8bc1<code>query.filled</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\uff0c<code>query.filled.get(0)</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>aggregate</code>\u3002
     * @throws Exception \u8bfb\u53d6\u6d4b\u8bd5\u6570\u636e\u6216\u7b49\u5f85\u6d4b\u8bd5\u7ed3\u679c\u5931\u8d25
     */
    @Test
    void shouldBridgeQueryAndAggregateResults() throws Exception {
        TrackingQuery query = new TrackingQuery();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("query", query);
        TestAggregate aggregate = new TestAggregate("1");

        new AggregateQueryResultFiller().fill(ParameterUtils.extractParameters(parameters), Collections.singletonList(aggregate));

        assertThat(query.filled).hasSize(1);
        assertThat(query.filled.get(0)).isSameAs(aggregate);
    }

    /** \u9a8c\u8bc1<code>query.filled</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\uff0c<code>query.filled.get(0)</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>aggregate</code>\u3002 */
    @Test
    void shouldUseEnhanceParameterNormalizationForMapperCollections() {
        TrackingQuery query = new TrackingQuery();
        TestAggregate aggregate = new TestAggregate("collection");
        new AggregateQueryResultFiller().fill(
                ParameterUtils.extractParameters(Collections.singletonMap("queries", Collections.singletonList(query))),
                Collections.singletonList(Page.succeed(Collections.singletonList(aggregate), 1, 1, 1)));
        assertThat(query.filled).hasSize(1);
        assertThat(query.filled.get(0)).isSameAs(aggregate);
    }

    /** \u8bb0\u5f55\u6216\u66b4\u9732\u7b5b\u9009\u6761\u4ef6\u7684\u67e5\u8be2\u66ff\u8eab\u3002 */
    static final class TrackingQuery extends Query<TestAggregate> {

        /** \u4fdd\u5b58\u5df2\u4ea4\u7ed9 ORM \u7ed3\u679c\u586b\u5145\u5165\u53e3\u7684\u6570\u636e\u3002 */
        private List<? extends AggregateRoot<?>> filled;

        /**
         * 补充当前查询执行所需的参数。
         *
         * <p>此实现更新<code>this.filled = models</code>。</p>
         *
         * @param models 待填充的聚合根列表
         */
        @Override
        public void doFills(List<? extends AggregateRoot<?>> models) {
            this.filled = models;
        }
    }

    /** \u6d4b\u8bd5\u4e13\u7528\u805a\u5408\uff0c\u4f9b\u9a8c\u8bc1\u805a\u5408\u6807\u8bc6\u548c\u4ed3\u50a8\u6216\u4e8b\u4ef6\u5904\u7406\u884c\u4e3a\u3002 */
    static final class TestAggregate extends AggregateRoot<String> {

        /** \u7528\u4e8e\u6784\u9020\u548c\u5b9a\u4f4d\u6d4b\u8bd5\u8bb0\u5f55\u7684\u4e3b\u952e\u3002 */
        private final String id;

        /**
         * \u4ee5\u7ed9\u5b9a\u72b6\u6001\u6784\u9020 <code>TestAggregate</code> \u6d4b\u8bd5\u5bf9\u8c61\u3002
         * @param id \u7528\u4e8e\u6784\u9020\u548c\u5b9a\u4f4d\u6d4b\u8bd5\u8bb0\u5f55\u7684\u4e3b\u952e
         */
        TestAggregate(String id) {
            this.id = id;
        }

        /**
         * \u8bfb\u53d6<code>id</code> \u5b57\u6bb5\u3002
         * @return <code>id</code> \u5b57\u6bb5
         */
        @Override
        public String id() {
            return id;
        }
    }
}
