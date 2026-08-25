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

    private static final long serialVersionUID = 1L;

    private String aggregateId;
    private long version;

    public StoredEventEntityId() {
    }

    public StoredEventEntityId(String aggregateId, long version) {
        this.aggregateId = aggregateId;
        this.version = version;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StoredEventEntityId that = (StoredEventEntityId) o;
        return version == that.version && Objects.equals(aggregateId, that.aggregateId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aggregateId, version);
    }
}
