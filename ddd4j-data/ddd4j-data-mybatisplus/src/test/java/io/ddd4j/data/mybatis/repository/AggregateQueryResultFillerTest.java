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

class AggregateQueryResultFillerTest {

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
