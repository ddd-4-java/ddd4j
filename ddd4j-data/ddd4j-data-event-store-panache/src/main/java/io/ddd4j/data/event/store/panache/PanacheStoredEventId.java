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
package io.ddd4j.data.event.store.panache;

import java.io.Serializable;
import java.util.Objects;

/**
 * {@link PanacheStoredEventEntity} 的复合主键类。
 *
 * <p>主键由 {@code aggregate_type} + {@code aggregate_id} + {@code version} 组成，
 * 保证同一聚合类型流内的版本号唯一。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public class PanacheStoredEventId implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 聚合类型。 */
    private String aggregateType;
    /** 聚合标识。 */
    private String aggregateId;
    /** 事件版本。 */
    private long version;

    /**
     * 创建空的复合主键，供 JPA 使用。
     */
    public PanacheStoredEventId() {
    }

    /**
     * 创建事件复合主键。
     *
     * @param aggregateType 聚合类型
     * @param aggregateId 聚合标识
     * @param version 事件版本
     */
    public PanacheStoredEventId(String aggregateType, String aggregateId, long version) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.version = version;
    }

    /**
     * 返回聚合标识。
     *
     * @return 聚合标识
     */
    public String getAggregateId() {
        return aggregateId;
    }

    /**
     * 返回聚合类型。
     *
     * @return 聚合类型
     */
    public String getAggregateType() {
        return aggregateType;
    }

    /**
     * 返回事件版本。
     *
     * @return 事件版本
     */
    public long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PanacheStoredEventId that = (PanacheStoredEventId) o;
        return version == that.version
                && Objects.equals(aggregateType, that.aggregateType)
                && Objects.equals(aggregateId, that.aggregateId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aggregateType, aggregateId, version);
    }
}
