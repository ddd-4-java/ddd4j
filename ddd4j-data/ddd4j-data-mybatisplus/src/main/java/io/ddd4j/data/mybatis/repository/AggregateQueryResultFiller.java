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

/**
 * 为 Mapper 直接返回的 DDD4J 聚合根填充关联数据。
 */
public final class AggregateQueryResultFiller {

    /**
     * 查找领域查询，统一填充结果中的聚合根。
     *
     * @param parameters ORM 已标准化的参数对象
     * @param results    查询结果
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
     * 从执行参数中查找需要结果填充的 DDD4J 查询。
     *
     * @param parameters 当前语句的调用参数
     * @return 找到的查询，缺少时为空
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
     * 从返回对象和容器中提取可用于结果填充的聚合实例。
     *
     * @param result     需要提取聚合对象的返回值
     * @param aggregates 本次批量写入的聚合对象
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
