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
package io.ddd4j.data.event.store.jpa;

import java.io.Serializable;
import java.util.Objects;

/**
 * {@link StoredEventEntity} 的复合主键类。
 *
 * <p>主键由 {@code aggregate_id} + {@code version} 组成，保证同一聚合内的版本号唯一。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public class StoredEventEntityId implements Serializable {

    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    private static final long serialVersionUID = 1L;

    /** \u805a\u5408\u6807\u8bc6\u3002 */
    private String aggregateId;
    /** \u805a\u5408\u7c7b\u578b\u3002 */
    private String aggregateType;
    /** \u4e8b\u4ef6\u7248\u672c\u3002 */
    private long version;

    /** \u521b\u5efa\u7a7a\u7684StoredEventEntityId\u5b9e\u4f8b\uff0c\u4f9b JPA \u6216\u5e8f\u5217\u5316\u6846\u67b6\u4f7f\u7528\u3002 */
    public StoredEventEntityId() {
    }

    /**
     * \u521b\u5efa\u4e8b\u4ef6\u590d\u5408\u4e3b\u952e\u3002
     * @param aggregateType \u805a\u5408\u7c7b\u578b
     * @param aggregateId \u805a\u5408\u6807\u8bc6
     * @param version \u4e8b\u4ef6\u7248\u672c
     */
    public StoredEventEntityId(String aggregateType, String aggregateId, long version) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.version = version;
    }

    /**
     * \u8fd4\u56de\u805a\u5408\u6807\u8bc6\u3002
     * @return \u805a\u5408\u6807\u8bc6
     */
    public String getAggregateId() {
        return aggregateId;
    }

    /**
     * \u8fd4\u56de\u805a\u5408\u7c7b\u578b\u3002
     * @return \u805a\u5408\u7c7b\u578b
     */
    public String getAggregateType() {
        return aggregateType;
    }

    /**
     * \u8fd4\u56de\u4e8b\u4ef6\u7248\u672c\u3002
     * @return \u4e8b\u4ef6\u7248\u672c
     */
    public long getVersion() {
        return version;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StoredEventEntityId that = (StoredEventEntityId) o;
        return version == that.version
                && Objects.equals(aggregateType, that.aggregateType)
                && Objects.equals(aggregateId, that.aggregateId);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(aggregateType, aggregateId, version);
    }
}
