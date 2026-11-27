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

import io.ddd4j.core.api.Page;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** 为 Mapper 直接返回的 DDD4J 聚合根填充关联数据。 */
public final class AggregateQueryResultFiller {

    /**
     * 查找领域查询，统一填充结果中的聚合根。
     * @param parameters ORM 已标准化的参数对象
     * @param results 查询结果
     */
    public void fill(Collection<Object> parameters, List<?> results) {
        if (results.isEmpty()) {
            return;
        }
        Query<?> query = findQuery(parameters);
        if (Objects.isNull(query)) {
            return;
        }
        List<AggregateRoot<?>> aggregates = new ArrayList<>();
        for (Object result : results) {
            collectAggregates(result, aggregates);
        }
        if (!aggregates.isEmpty()) {
            query.doFills(aggregates);
        }
    }

    /**
     * \u4ece\u6267\u884c\u53c2\u6570\u4e2d\u67e5\u627e\u9700\u8981\u7ed3\u679c\u586b\u5145\u7684 DDD4J \u67e5\u8be2\u3002
     * @param parameters \u5f53\u524d\u8bed\u53e5\u7684\u8c03\u7528\u53c2\u6570
     * @return \u627e\u5230\u7684\u67e5\u8be2\uff0c\u7f3a\u5c11\u65f6\u4e3a\u7a7a
     */
    private Query<?> findQuery(Collection<Object> parameters) {
        for (Object candidate : parameters) {
            if (candidate instanceof Query<?>) {
                return (Query<?>) candidate;
            }
        }
        return null;
    }

    /**
     * \u4ece\u8fd4\u56de\u5bf9\u8c61\u548c\u5bb9\u5668\u4e2d\u63d0\u53d6\u53ef\u7528\u4e8e\u7ed3\u679c\u586b\u5145\u7684\u805a\u5408\u5b9e\u4f8b\u3002
     * @param result \u9700\u8981\u63d0\u53d6\u805a\u5408\u5bf9\u8c61\u7684\u8fd4\u56de\u503c
     * @param aggregates \u672c\u6b21\u6279\u91cf\u5199\u5165\u7684\u805a\u5408\u5bf9\u8c61
     */
    private void collectAggregates(Object result, List<AggregateRoot<?>> aggregates) {
        if (result instanceof AggregateRoot<?>) {
            aggregates.add((AggregateRoot<?>) result);
        } else if (result instanceof Page<?>) {
            List<?> records = ((Page<?>) result).getRecords();
            if (Objects.nonNull(records)) {
                for (Object record : records) {
                    collectAggregates(record, aggregates);
                }
            }
        }
    }
}
