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
import io.ddd4j.core.api.Page;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通过 <code>parameters.put()</code>、<code>ParameterUtils.extractParameters()</code>、<code>Collections.singletonList()</code> 验证 <code>AggregateQueryResultFiller</code> 的运行行为。
 */
class AggregateQueryResultFillerTest {

    /**
     * 验证<code>query.filled</code> 的大小为 <code>1</code>，<code>query.filled.get(0)</code> 指向同一实例 <code>aggregate</code>。
     *
     * @throws Exception 读取测试数据或等待测试结果失败
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

    /**
     * 验证<code>query.filled</code> 的大小为 <code>1</code>，<code>query.filled.get(0)</code> 指向同一实例 <code>aggregate</code>。
     */
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

    /**
     * 记录或暴露筛选条件的查询替身。
     */
    static final class TrackingQuery extends Query<TestAggregate> {

        /**
         * 保存已交给 ORM 结果填充入口的数据。
         */
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

    /**
     * 测试专用聚合，供验证聚合标识和仓储或事件处理行为。
     */
    static final class TestAggregate extends AggregateRoot<String> {

        /**
         * 用于构造和定位测试记录的主键。
         */
        private final String id;

        /**
         * 以给定状态构造 <code>TestAggregate</code> 测试对象。
         *
         * @param id 用于构造和定位测试记录的主键
         */
        TestAggregate(String id) {
            this.id = id;
        }

        /**
         * 读取<code>id</code> 字段。
         *
         * @return <code>id</code> 字段
         */
        @Override
        public String id() {
            return id;
        }
    }
}
